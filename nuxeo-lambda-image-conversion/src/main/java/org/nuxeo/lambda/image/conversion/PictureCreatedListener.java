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

import static org.nuxeo.ecm.platform.picture.api.ImagingDocumentConstants.PICTURE_FACET;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.platform.picture.api.ImagingService;
import org.nuxeo.ecm.platform.picture.api.PictureConversion;
import org.nuxeo.lambda.core.LambdaInput;
import org.nuxeo.lambda.core.LambdaService;
import org.nuxeo.runtime.api.Framework;

/**
 * Listener used to schedule lambda task when Picture documents are updated.
 */
public class PictureCreatedListener implements EventListener {

    private static final Log log = LogFactory.getLog(PictureCreatedListener.class);

    public static final String DISABLE_PICTURE_VIEWS_GENERATION_LISTENER = "disablePictureViewsGenerationListener";

    public static final String DEFAULT_LAMBDA_PICTURE_NAME = "nuxeo-lambda-picture";

    public static final String BUCKET = "bucket";

    public static final String DIGEST_PROP = "digest";

    public static final String MIME_TYPE = "mimeType";

    public static final String FILENAME = "filename";

    public static final String LIFECYCLE = "lifecycle";

    public static final String CONVERSIONS_SIZES = "sizes";

    public static final String DOC_ID_PROP = "docId";

    public static final String REPOSITORY_PROP = "repository";

    @Override
    public void handleEvent(Event event) {
        EventContext ctx = event.getContext();
        if (!(ctx instanceof DocumentEventContext)) {
            return;
        }

        Boolean block = (Boolean) event.getContext().getProperty(DISABLE_PICTURE_VIEWS_GENERATION_LISTENER);
        if (Boolean.TRUE.equals(block)) {
            // ignore the event - we are blocked by the caller
            return;
        }

        DocumentEventContext docCtx = (DocumentEventContext) ctx;
        DocumentModel doc = docCtx.getSourceDocument();
        if (doc.hasFacet(PICTURE_FACET) && !doc.isProxy()) {
            Property content = doc.getProperty("file:content");
            Blob blob = (Blob) content.getValue();

            if (blob == null) {
                log.debug("Attempt to create Picture View with no data");
                return;
            }
            String digest = blob.getDigest();

            LambdaService service = Framework.getService(LambdaService.class);

            Map<String, Serializable> lambdaInput = new HashMap<>();

            lambdaInput.put(LIFECYCLE, doc.getCurrentLifeCycleState());
            lambdaInput.put(DIGEST_PROP, digest);
            lambdaInput.put(BUCKET, Framework.getProperty("nuxeo.s3storage.bucket"));
            lambdaInput.put(MIME_TYPE, blob.getMimeType());
            lambdaInput.put(FILENAME, blob.getFilename());

            JSONObject conversionsJSON = new JSONObject();
            ImagingService imagingService = Framework.getService(ImagingService.class);

            List<PictureConversion> conversions = imagingService.getPictureConversions();
            log.debug("Found " + conversions.size() + " PictureConversions");
            conversions.forEach(conv -> {
                try {
                    conversionsJSON.put(conv.getId(), conv.getMaxSize());
                } catch (JSONException e) {
                    log.error("Couldn't put conversion into JSON", e);
                }
            });

            // Seems weird to convert a JSONObject to string before passing to AWS but it works...
            lambdaInput.put(CONVERSIONS_SIZES, conversionsJSON.toString());

            String lambdaName = Framework.getProperty("nuxeo.lambda.image.conversion", DEFAULT_LAMBDA_PICTURE_NAME);

            try {
                Map<String, Serializable> params = new HashMap<>();
                params.put(DOC_ID_PROP, doc.getId());
                params.put(REPOSITORY_PROP, doc.getRepositoryName());

                LambdaInput input = new LambdaInput(lambdaInput);
                service.scheduleCall(lambdaName, params, input);
            } catch (Exception e) {
                log.error("Error while calling Lambda", e);
            }

        }
    }
}