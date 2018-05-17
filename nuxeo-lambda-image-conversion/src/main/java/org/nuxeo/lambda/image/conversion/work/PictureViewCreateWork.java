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
package org.nuxeo.lambda.image.conversion.work;

import static org.nuxeo.ecm.core.api.CoreSession.ALLOW_VERSION_WRITE;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.binary.BinaryBlob;
import org.nuxeo.ecm.core.blob.binary.BinaryManager;
import org.nuxeo.ecm.core.blob.binary.CachingBinaryManager;
import org.nuxeo.ecm.core.blob.binary.LazyBinary;
import org.nuxeo.ecm.core.versioning.VersioningService;
import org.nuxeo.lambda.core.LambdaService;
import org.nuxeo.ecm.platform.picture.PictureViewsGenerationWork;
import org.nuxeo.ecm.platform.picture.api.PictureView;
import org.nuxeo.lambda.image.conversion.common.ImageProperties;
import org.nuxeo.runtime.api.Framework;

public class PictureViewCreateWork extends PictureViewsGenerationWork {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(PictureViewCreateWork.class);

    public static final String CATEGORY_PICTURE_LAMBDA_GENERATION = "pictureViewWithLambdaGeneration";

    private List<ImageProperties> properties;

    public PictureViewCreateWork(String repositoryName, String docId, String xpath, List<ImageProperties> properties) {
        super(repositoryName, docId, xpath);
        this.properties = properties;
    }

    @Override
    public void work() {
        if (properties == null || properties.isEmpty()) {
            log.debug("Digest is null");
            throw new NuxeoException("Cannot work without digests");
        }
        openSystemSession();

        DocumentModel document = session.getDocument(new IdRef(docId));
        String filename = (String) document.getPropertyValue("file:content/name");
        filename = removeExtension(filename) + ".png";
        String repositoryName = document.getRepositoryName();

        log.debug("received " + properties.size() + " properties");
        List<Map<String, ? extends Serializable>> views = new ArrayList<>();
        for (ImageProperties property : properties) {
            String digest = property.getDigest();
            BinaryBlob blob = new BinaryBlob(
                    new LazyBinary(digest, repositoryName, getCachingBinaryManager(repositoryName)), digest, filename,
                    "image/png", null, digest, property.getLength());

            // XXX here you need to get more info from the lambda
            // probably passing some JSON from the lambda back to the Callback

            Map<String, Serializable> m = new HashMap<>();
            m.put(PictureView.FIELD_TITLE, property.getName());
            m.put(PictureView.FIELD_DESCRIPTION, "");
            m.put(PictureView.FIELD_TAG, property.getName());
            m.put(PictureView.FIELD_HEIGHT, property.getHeight().toString());
            m.put(PictureView.FIELD_WIDTH, property.getWidth().toString());
            m.put(PictureView.FIELD_FILENAME, filename);
            m.put(PictureView.FIELD_CONTENT, blob);
            m.put(PictureView.FIELD_INFO, (Serializable) property.toMap(filename));
            views.add(m);
        }

        document.setPropertyValue("picture:views", (Serializable) views);

        setStatus("Saving");
        if (document.isVersion()) {
            document.putContextData(ALLOW_VERSION_WRITE, Boolean.TRUE);
        }
        document.putContextData("disablePictureViewsGenerationListener", Boolean.TRUE);
        document.putContextData("disableNotificationService", Boolean.TRUE);
        document.putContextData("disableAuditLogger", Boolean.TRUE);
        document.putContextData(VersioningService.DISABLE_AUTO_CHECKOUT, Boolean.TRUE);
        session.saveDocument(document);
        setStatus("Saved");
        log.debug("Document saved");
        firePictureViewsGenerationDoneEvent(document);

        // tell metrics that we have indeed finished integrating the lambda result
        LambdaService ls = Framework.getService(LambdaService.class);
        ls.getMetrics().markIntegrated();
    }

    @Override
    public String getTitle() {
        return null;
    }

    @Override
    public String getCategory() {
        return CATEGORY_PICTURE_LAMBDA_GENERATION;
    }

    protected CachingBinaryManager getCachingBinaryManager(String repository) {
        BlobManager bm = Framework.getService(BlobManager.class);
        BlobProvider bp = bm.getBlobProvider(repository);
        BinaryManager b = bp.getBinaryManager();

        if (b instanceof CachingBinaryManager) {
            return (CachingBinaryManager) b;
        }
        // in test with local BM ...
        return null;
    }

    private String removeExtension(String path) {
        int i = path.indexOf(46);
        return i == -1 ? path : path.substring(0, i);
    }
}
