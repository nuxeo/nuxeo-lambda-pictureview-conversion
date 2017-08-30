/*
 * (C) Copyright 2006-2017 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 * Contributors:
 *     anechaev
 */
package org.nuxeo.ecm.lambda.image.conversion;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.lambda.core.service.LambdaService;
import org.nuxeo.ecm.platform.picture.PictureViewsGenerationWork;
import org.nuxeo.runtime.api.Framework;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.nuxeo.ecm.lambda.core.service.LambdaServiceConstants.*;
import static org.nuxeo.ecm.lambda.integration.endpoint.service.LambdaResponseAcceptor.LAMBDA_EVENT_NAME;
import static org.nuxeo.ecm.lambda.integration.endpoint.service.LambdaResponseAcceptor.LAMBDA_FAILED_EVENT_NAME;


public class LambdaResponseListener implements EventListener {

    public static final Log log = LogFactory.getLog(LambdaResponseListener.class);

    protected static final String XPATH = "file:content";

    @SuppressWarnings("unchecked")
    @Override
    public void handleEvent(Event event) {
        EventContext ctx = event.getContext();
        Map<String, Serializable> params = (Map<String, Serializable>) ctx.getProperty(PARAMETERS_KEY);

        if (event.getName().equals(LAMBDA_EVENT_NAME)) {
            JSONObject object = (JSONObject) ctx.getProperty(LAMBDA_RESPONSE_KEY);
            if (params == null || object == null) {
                log.warn("Couldn't get any data from the context");
                return;
            }

            try {
                JSONArray json = object.getJSONArray("images");
                List<ImageProperties> props = new ArrayList<>(json.length());
                for (int i = 0; i < json.length(); i++) {
                    props.add(
                            new ImageProperties(json.getJSONObject(i))
                    );
                }

                final String repoName = (String) params.get(REPOSITORY_PROP);
                final String docId = (String) params.get(DOC_ID_PROP);
                PictureViewCreateWork work = new PictureViewCreateWork(repoName, docId, XPATH, props);
                WorkManager manager = Framework.getService(WorkManager.class);
                manager.schedule(work, WorkManager.Scheduling.IF_NOT_SCHEDULED, true);
            } catch (JSONException e) {
                log.error(e);
            }
        } else if (event.getName().equals(LAMBDA_FAILED_EVENT_NAME)) {
            String docId = (String) params.get(DOC_ID_PROP);
            String repoName = (String) params.get(REPOSITORY_PROP);

            PictureViewsGenerationWork work = new PictureViewsGenerationWork(repoName, docId, XPATH);
            WorkManager workManager = Framework.getService(WorkManager.class);
            workManager.schedule(work, WorkManager.Scheduling.IF_NOT_SCHEDULED, true);

            LambdaService ls = Framework.getService(LambdaService.class);
            ls.getMetrics().markIntegrated();
        }
    }
}
