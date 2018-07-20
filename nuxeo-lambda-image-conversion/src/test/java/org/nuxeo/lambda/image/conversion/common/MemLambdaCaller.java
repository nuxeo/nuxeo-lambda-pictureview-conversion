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

import java.util.Arrays;
import java.util.concurrent.Executors;

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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * LambdaCaller Mocking class to imitate response from actual AWS Lambda.
 */
public class MemLambdaCaller extends AbstractLambdaCaller {

    public static volatile String repositoryName;

    public static volatile boolean completed;

    public static volatile Exception exception;

    @Override
    protected boolean call(String functionName, ObjectNode input) {
        // create the view in an async way
        Executors.newSingleThreadExecutor().submit(() -> {
            String callbackId = input.get(LambdaService.CALLBACK_PROP).asText();
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

                ArrayNode properties = new ArrayNode(JsonNodeFactory.instance);
                for (Blob blob : Arrays.asList(blob1, blob2, blob3)) {
                    // save the binary
                    Binary binary = binaryManager.getBinary(blob);
                    // build the response
                    ObjectNode property = new ObjectNode(JsonNodeFactory.instance);
                    property.put(ImageProperties.IMAGE_DIGEST, binary.getDigest());
                    property.put(ImageProperties.IMAGE_WIDTH, 100);
                    property.put(ImageProperties.IMAGE_HEIGHT, 100);
                    property.put(ImageProperties.IMAGE_NAME, blob.getFilename());
                    property.put(ImageProperties.IMAGE_LENGTH, 1001);
                    properties.add(property);
                }

                ObjectNode response = new ObjectNode(JsonNodeFactory.instance);
                response.put("images", properties);
                lambdaService.onResult(callbackId, LambdaService.LambdaStatus.SUCCESS, response);

            } catch (Throwable e) {
                exception = new NuxeoException(e);
                lambdaService.onResult(callbackId, LambdaService.LambdaStatus.ERROR,
                        new ObjectNode(JsonNodeFactory.instance));
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
