/*
 * (C) Copyright 2006-2018 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.lambda.image.conversion.common;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.binary.Binary;
import org.nuxeo.ecm.core.blob.binary.BinaryManager;
import org.nuxeo.lambda.core.AbstractLambdaCaller;
import org.nuxeo.lambda.core.LambdaService;
import org.nuxeo.runtime.api.Framework;

import java.util.Arrays;
import java.util.concurrent.Executors;

public class MemLambdaCaller extends AbstractLambdaCaller {

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
                lambdaService.onResult(callbackId, LambdaService.LambdaStatus.SUCCESS, response);

            } catch (Throwable e) {
                exception = new NuxeoException(e);
                lambdaService.onResult(callbackId, LambdaService.LambdaStatus.ERROR, new JSONObject());
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
