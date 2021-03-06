/*
 * Copyright 2011-2017 CaboLabs Health Informatics
 *
 * The EHRServer was designed and developed by Pablo Pazos Gutierrez <pablo.pazos@cabolabs.com> at CaboLabs Health Informatics (www.cabolabs.com).
 *
 * You can't remove this notice from the source code, you can't remove the "Powered by CaboLabs" from the UI, you can't remove this notice from the window that appears then the "Powered by CaboLabs" link is clicked.
 *
 * Any modifications to the provided source code can be stated below this notice.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cabolabs.ehrserver.openehr.common.change_control

import org.springframework.dao.DataIntegrityViolationException

import grails.plugin.springsecurity.SpringSecurityUtils

import com.cabolabs.security.Organization
import com.cabolabs.security.User
import com.cabolabs.ehrserver.openehr.common.change_control.Contribution

import grails.util.Holders

class ContributionController {

   def springSecurityService
   
   static allowedMethods = [save: "POST", update: "POST", delete: "POST"]

   def config = Holders.config.app
   
   def index()
   {
      redirect(action: "list", params: params)
   }

   def list(int max, int offset, String sort, String order, String ehdUid, String orgUid)
   {
      max = Math.min(max ?: config.list_max, 100)
      if (!offset) offset = 0
      if (!sort) sort = 'id'
      if (!order) order = 'desc'
     
      def list, org
      def c = Contribution.createCriteria()
      
      if (orgUid)
      {
         if (Organization.countByUid(orgUid) == 0)
         {
            flash.message = "contribution.list.feedback.orgNotFoundShowingForCurrentOrg"
            orgUid = null
         }
         else
         {
            // Have access to orgUid?
            def us = User.findByUsername(springSecurityService.authentication.principal.username)
            if (!us.organizations.uid.contains(orgUid) && !SpringSecurityUtils.ifAllGranted("ROLE_ADMIN"))
            {
               flash.message = "contribution.list.feedback.cantAccessOrgShowingForCurrentOrg"
               orgUid = null
            }
         }
      }
      
      if (SpringSecurityUtils.ifAllGranted("ROLE_ADMIN"))
      {
         list = c.list (max: max, offset: offset, sort: sort, order: order) {
           
            // for admins, if not orgUid, display for all orgs
            
            if (orgUid)
            {
               ehr {
                 eq("organizationUid", orgUid)
               }
            }
            
            if (ehdUid)
            {
               ehr {
                  like('uid', '%'+ehdUid+'%')
               }
            }
         }
      }
      else
      {
         // auth token used to login
         def auth = springSecurityService.authentication
         org = Organization.findByNumber(auth.organization)
         
         list = c.list (max: max, offset: offset, sort: sort, order: order) {
            if (!orgUid)
            {
               flash.message = message(code:"contribution.list.feedback.showingForCurrentOrg")
               eq("organizationUid", org.uid)
            }
            else
            {
               eq("organizationUid", orgUid)
            }
            if (ehdUid)
            {
               ehr {
                  like('uid', '%'+ehdUid+'%')
               }
            }
         }
      }
     
      // =========================================================================
      // For charting
      
      // Show 1 year by month
      def now = new Date()
      def oneyearbehind = now - 365
      
      def data = Contribution.withCriteria {
          projections {
              count('id')
              groupProperty('yearMonthGroup') // count contributions in the same month
          }
          if (!SpringSecurityUtils.ifAllGranted("ROLE_ADMIN"))
          {
             eq('organizationUid', org.uid)
          }
          audit {
             between('timeCommitted', oneyearbehind, now)
          }
      }
      
      //println data
      // =========================================================================

      return [contributionInstanceList: list, total: list.totalCount,
              data: data, start: oneyearbehind, end: now]
   }

   def show(Long id)
   {
      def contributionInstance = Contribution.get(id)
      if (!contributionInstance) {
         flash.message = message(code: 'default.not.found.message', args: [message(code: 'contribution.label', default: 'Contribution'), id])
         redirect(action: "list")
         return
      }

      [contributionInstance: contributionInstance]
   }
}
