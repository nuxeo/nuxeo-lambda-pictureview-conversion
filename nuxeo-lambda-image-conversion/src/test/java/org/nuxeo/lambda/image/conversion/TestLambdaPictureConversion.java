/*
 * (C) Copyright 2017 Nuxeo (http://nuxeo.com/) and others.
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
 *     Kevin Leturc <kleturc@nuxeo.com>
 */
package org.nuxeo.lambda.image.conversion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.binary.Binary;
import org.nuxeo.ecm.core.blob.binary.BinaryManager;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.lambda.core.AbstractLambdaCaller;
import org.nuxeo.lambda.core.LambdaService;
import org.nuxeo.lambda.core.LambdaService.LambdaStatus;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;
import org.nuxeo.runtime.transaction.TransactionHelper;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class })
@Deploy({ "org.nuxeo.lambda.core", "org.nuxeo.lambda.image.conversion", "org.nuxeo.ecm.platform.picture.api",
                "org.nuxeo.ecm.platform.picture.core", "org.nuxeo.ecm.core.cache", "org.nuxeo.ecm.automation.core" })
@LocalDeploy("org.nuxeo.lambda.image.conversion.test:OSGI-INF/lambda-picture-config-override-contrib.xml")
public class TestLambdaPictureConversion {

    @Inject
    private CoreFeature coreFeature;

    @Inject
    private CoreSession session;

    @Before
    public void clear() {
        MemLambdaCaller.clear();
        MemLambdaCaller.repositoryName = session.getRepositoryName();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testLambdaPictureConversion() throws Exception {
        DocumentModel pictureDoc = session.createDocumentModel("/", "MyPicture", "File");
        pictureDoc.addFacet("Picture");

        File image = new File(getClass().getClassLoader().getResource("binaries/nuxeo.png").getFile());
        Blob blob = new FileBlob(image);
        pictureDoc.setPropertyValue("file:content", (Serializable) blob);
        pictureDoc = session.createDocument(pictureDoc);
        // commit transaction
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();
        // wait for the end of lambda computation
        while (!MemLambdaCaller.completed) {
            Thread.sleep(100);
        }
        // wait for the listener to put images in document
        coreFeature.waitForAsyncCompletion();
        if (MemLambdaCaller.exception != null) {
            AssertionError exception = new AssertionError();
            exception.addSuppressed(MemLambdaCaller.exception);
            throw exception;
        }

        pictureDoc = session.getDocument(pictureDoc.getRef());
        List<Map<String, Serializable>> pictureViews = (List<Map<String, Serializable>>) pictureDoc.getPropertyValue("picture:views");
        assertNotNull(pictureViews);
        assertEquals(3, pictureViews.size());
    }

    public static class MemLambdaCaller extends AbstractLambdaCaller {

        public static volatile String repositoryName = null;

        public static volatile boolean completed = false;

        public static volatile Exception exception = null;

        @Override
        protected boolean call(String functionName, JSONObject input) {
            // create the view in an async way
            Executors.newSingleThreadExecutor().submit(() -> {
                String callbackId = input.optString(LambdaService.CALLBACK_PROP);
                LambdaService lambdaService = Framework.getService(LambdaService.class);
                try {
                    // sleep a bit to let AbstractLambdaCaller put callback in K/V store
                    Thread.sleep(100);
                    // generate and fill binary manager
                    // this code is run during a transaction commit
                    BlobManager blobManager = Framework.getService(BlobManager.class);
                    BlobProvider blobProvider = blobManager.getBlobProvider(repositoryName);
                    BinaryManager binaryManager = blobProvider.getBinaryManager();

                    Blob blob1 = new StringBlob("viewOne");
                    blob1.setFilename("viewOne.png");

                    Blob blob2 = new StringBlob("viewTwo");
                    blob2.setFilename("viewTwo.png");

                    Blob blob3 = new StringBlob("viewTree");
                    blob3.setFilename("viewTree.png");

                    JSONArray properties = new JSONArray();
                    for (Blob blob : Arrays.asList(blob1, blob2, blob3)) {
                        // save the binary
                        Binary binary = binaryManager.getBinary(blob);
                        // build the response
                        JSONObject property = new JSONObject();
                        property.put(ImageProperties.IMAGE_DIGEST, binary.getDigest());
                        property.put(ImageProperties.IMAGE_WIDTH, 100);
                        property.put(ImageProperties.IMAGE_HEIGHT, 100);
                        property.put(ImageProperties.IMAGE_NAME, blob.getFilename());
                        property.put(ImageProperties.IMAGE_LENGTH, 1001);
                        properties.put(property);
                    }

                    JSONObject response = new JSONObject();
                    response.put("images", properties);
                    lambdaService.onResult(callbackId, LambdaStatus.SUCCESS, response);

                } catch (Exception e) {
                    exception = e;
                    lambdaService.onResult(callbackId, LambdaStatus.ERROR, new JSONObject());
                } finally {
                    completed = true;
                }
            });
            return true;
        }

        public static void clear() {
            repositoryName = null;
            completed = false;
            exception = null;
        }
    }

}
