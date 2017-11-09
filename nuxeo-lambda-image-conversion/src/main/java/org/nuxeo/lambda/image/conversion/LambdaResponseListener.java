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
package org.nuxeo.lambda.image.conversion;

import static org.nuxeo.lambda.core.LambdaService.LAMBDA_RESPONSE_KEY;
import static org.nuxeo.lambda.core.LambdaService.PARAMETERS_KEY;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.platform.picture.PictureViewsGenerationWork;
import org.nuxeo.lambda.core.LambdaService;
import org.nuxeo.runtime.api.Framework;

/**
 * @since 9.3
 */
public class LambdaResponseListener implements EventListener {

    public static final Log log = LogFactory.getLog(LambdaResponseListener.class);

    public static final String LAMBDA_SUCCESS_EVENT_NAME = "afterLambdaPictureResponse";

    public static final String LAMBDA_ERROR_EVENT_NAME = "lambdaPictureFailed";

    protected static final String XPATH = "file:content";

    @SuppressWarnings("unchecked")
    @Override
    public void handleEvent(Event event) {
        EventContext ctx = event.getContext();
        Map<String, Serializable> params = (Map<String, Serializable>) ctx.getProperty(PARAMETERS_KEY);

        if (LAMBDA_SUCCESS_EVENT_NAME.equals(event.getName())) {
            JSONObject object = (JSONObject) ctx.getProperty(LAMBDA_RESPONSE_KEY);
            if (params == null || object == null) {
                log.warn("Couldn't get any data from the context");
                return;
            }

            try {
                JSONArray json = object.getJSONArray("images");
                List<ImageProperties> props = new ArrayList<>(json.length());
                for (int i = 0; i < json.length(); i++) {
                    props.add(new ImageProperties(json.getJSONObject(i)));
                }

                String repoName = (String) params.get(PictureCreatedListener.REPOSITORY_PROP);
                String docId = (String) params.get(PictureCreatedListener.DOC_ID_PROP);
                PictureViewCreateWork work = new PictureViewCreateWork(repoName, docId, XPATH, props);
                WorkManager manager = Framework.getService(WorkManager.class);
                manager.schedule(work, WorkManager.Scheduling.IF_NOT_SCHEDULED, true);
            } catch (JSONException e) {
                log.error(e);
            }
        } else if (LAMBDA_ERROR_EVENT_NAME.equals(event.getName())) {
            // fallback on default picture view generation
            String docId = (String) params.get(PictureCreatedListener.DOC_ID_PROP);
            String repoName = (String) params.get(PictureCreatedListener.REPOSITORY_PROP);

            PictureViewsGenerationWork work = new PictureViewsGenerationWork(repoName, docId, XPATH);
            WorkManager workManager = Framework.getService(WorkManager.class);
            workManager.schedule(work, WorkManager.Scheduling.IF_NOT_SCHEDULED, true);

            LambdaService ls = Framework.getService(LambdaService.class);
            ls.getMetrics().markIntegrated();
        }
    }
}
