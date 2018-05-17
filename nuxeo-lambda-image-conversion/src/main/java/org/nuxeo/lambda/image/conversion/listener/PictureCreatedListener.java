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
package org.nuxeo.lambda.image.conversion.listener;

import static org.nuxeo.ecm.platform.picture.api.ImagingDocumentConstants.PICTURE_FACET;
import static org.nuxeo.ecm.platform.picture.listener.PictureViewsGenerationListener.DISABLE_PICTURE_VIEWS_GENERATION_LISTENER;
import static org.nuxeo.lambda.image.conversion.common.Constants.BUCKET;
import static org.nuxeo.lambda.image.conversion.common.Constants.BUCKET_PREFIX;
import static org.nuxeo.lambda.image.conversion.common.Constants.BUCKET_PREFIX_PROP;
import static org.nuxeo.lambda.image.conversion.common.Constants.BUCKET_PROP;
import static org.nuxeo.lambda.image.conversion.common.Constants.CONVERSIONS_SIZES;
import static org.nuxeo.lambda.image.conversion.common.Constants.DEFAULT_LAMBDA_PICTURE_NAME;
import static org.nuxeo.lambda.image.conversion.common.Constants.DIGEST_PROP;
import static org.nuxeo.lambda.image.conversion.common.Constants.DOC_ID_PROP;
import static org.nuxeo.lambda.image.conversion.common.Constants.FILENAME;
import static org.nuxeo.lambda.image.conversion.common.Constants.LIFECYCLE;
import static org.nuxeo.lambda.image.conversion.common.Constants.MIME_TYPE;
import static org.nuxeo.lambda.image.conversion.common.Constants.NUXEO_LAMBDA_IMAGE_PROP;
import static org.nuxeo.lambda.image.conversion.common.Constants.REPOSITORY_PROP;
import static org.nuxeo.lambda.image.conversion.listener.LambdaResponseListener.XPATH;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
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
            Property content = doc.getProperty(XPATH);
            Blob blob = (Blob) content.getValue();

            if (blob == null) {
                log.debug("Attempt to create Picture View with no data");
                return;
            }

            Map<String, Serializable> lambdaInput = buildInput(doc, blob);
            LambdaInput input = new LambdaInput(lambdaInput);

            Map<String, Serializable> params = new HashMap<>();
            params.put(DOC_ID_PROP, doc.getId());
            params.put(REPOSITORY_PROP, doc.getRepositoryName());

            String lambdaName = Framework.getProperty(NUXEO_LAMBDA_IMAGE_PROP, DEFAULT_LAMBDA_PICTURE_NAME);

            try {
                LambdaService service = Framework.getService(LambdaService.class);
                service.scheduleCall(lambdaName, params, input);
            } catch (Exception e) {
                log.error("Error while calling Lambda", e);
            }
        }
    }

    protected Map<String, Serializable> buildInput(DocumentModel doc, Blob blob) {
        Map<String, Serializable> lambdaInput = new HashMap<>();

        lambdaInput.put(LIFECYCLE, doc.getCurrentLifeCycleState());
        lambdaInput.put(DIGEST_PROP, blob.getDigest());

        String bucket = Framework.getProperty(BUCKET_PROP);
        if (StringUtils.isEmpty(bucket)) {
            throw new NuxeoException("Cannot create lambda with no "
                    + BUCKET_PREFIX_PROP + " property defined");
        }

        lambdaInput.put(BUCKET, bucket);
        lambdaInput.put(MIME_TYPE, blob.getMimeType());
        lambdaInput.put(FILENAME, blob.getFilename());

        String prefix = Framework.getProperty(BUCKET_PREFIX_PROP);
        if (StringUtils.isNoneEmpty(prefix)) {
            lambdaInput.put(BUCKET_PREFIX, prefix);
        }

        JSONObject convJSON = getConversions();
        // Seems weird to convert a JSONObject to string before passing to AWS but it is the way it works...
        lambdaInput.put(CONVERSIONS_SIZES, convJSON.toString());

        return lambdaInput;
    }

    protected JSONObject getConversions() {
        JSONObject conversionsJSON = new JSONObject();
        ImagingService imagingService = Framework.getService(ImagingService.class);

        List<PictureConversion> conversions = imagingService.getPictureConversions();
        if (log.isDebugEnabled()) {
            log.debug("Found " + conversions.size() + " PictureConversions");
        }
        conversions.forEach(conv -> {
            try {
                conversionsJSON.put(conv.getId(), conv.getMaxSize());
            } catch (JSONException e) {
                log.error("Couldn't put conversion into JSON", e);
            }
        });

        return conversionsJSON;
    }
}
