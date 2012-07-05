/*
 * =========================================================
 * Hippo CMS - Konakart
 * https://bitbucket.org/jmirc/hippo-cms-konakart
 * =========================================================
 * Copyright 2012
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================================================
 */

package org.onehippo.forge.konakart.hst.restapi;

import com.konakart.al.KKAppEng;
import com.konakart.app.KKException;
import com.konakart.appif.ZoneIf;
import org.hippoecm.hst.jaxrs.services.AbstractResource;
import org.onehippo.forge.konakart.hst.model.SelectRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Path("/checkout/")
public class CheckoutResource extends AbstractResource {

    protected Logger log = LoggerFactory.getLogger(CheckoutResource.class);

    @GET
    @Path("states/{country}/")
    @Produces("application/json")
    public List<SelectRepresentation> getStatesResources(@Context HttpServletRequest servletRequest,
                                           @Context HttpServletResponse servletResponse, @Context UriInfo uriInfo,
                                           @PathParam("country") Integer country) {

        if (country == null || country == -1) {
            return Collections.emptyList();
        }

        List<SelectRepresentation> states = new ArrayList<SelectRepresentation>();

        KKAppEng kkAppEng = (KKAppEng) servletRequest.getSession().getAttribute(KKAppEng.KONAKART_KEY);

        if (kkAppEng != null) {
            // retrieve the list of province
            try {
                ZoneIf[] zones = kkAppEng.getEng().getZonesPerCountry(country);

                for (ZoneIf zone : zones) {
                    SelectRepresentation state = new SelectRepresentation();
                    state.setId(zone.getZoneId());
                    state.setName(zone.getZoneName());
                    states.add(state);
                }

            } catch (KKException e) {
                log.error("Unable to retrieve the list of states for the country id - " + country, e);
            }
        }

        return states;

    }
}
