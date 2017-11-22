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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.binary.BinaryManager;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.transaction.TransactionHelper;

@RunWith(FeaturesRunner.class)
@Features({ CoreFeature.class, PlatformFeature.class })
@Deploy({ "org.nuxeo.lambda.core", "org.nuxeo.ecm.platform.tag", "org.nuxeo.ecm.platform.picture.api",
                "org.nuxeo.ecm.platform.picture.core", "org.nuxeo.ecm.core.cache", "org.nuxeo.ecm.automation.core" })
public class TestPictureViewCreateWork {

    @Inject
    private CoreSession session;

    protected List<ImageProperties> prefillBinaryManager() throws Exception {
        BlobManager bm = Framework.getService(BlobManager.class);
        BlobProvider bp = bm.getBlobProvider(session.getRepositoryName());

        Blob blob1 = new StringBlob("viewOne");
        blob1.setFilename("viewOne.png");

        Blob blob2 = new StringBlob("viewTwo");
        blob2.setFilename("viewTwo.png");

        Blob blob3 = new StringBlob("viewTree");
        blob3.setFilename("viewTree.png");

        List<Blob> blobs = Arrays.asList(blob1, blob2, blob3);
        BinaryManager binaryManager = bp.getBinaryManager();

        List<ImageProperties> properties = new ArrayList<>();
        for (Blob blob : blobs) {
            ImageProperties property = new ImageProperties();
            property.setDigest(binaryManager.getBinary(blob).getDigest());
            property.setWidth(100);
            property.setHeight(100);
            property.setName(blob.getFilename());
            property.setLength(1001);
            properties.add(property);
        }

        return properties;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldCompleteWork() throws Exception {

        File image = new File(getClass().getClassLoader().getResource("binaries/nuxeo.png").getFile());

        Blob blob = new FileBlob(image);
        blob.setFilename("nuxeo.png");

        List<ImageProperties> digests = prefillBinaryManager();

        DocumentModel pictureDoc = session.createDocumentModel("/", "MyPicture", "File");

        pictureDoc.addFacet("Picture");
        pictureDoc.setPropertyValue("file:content", (Serializable) blob);
        pictureDoc = session.createDocument(pictureDoc);

        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();

        assertNotNull(pictureDoc);
        PictureViewCreateWork work = new PictureViewCreateWork(session.getRepositoryName(), pictureDoc.getId(),
                                                               "file:content", digests);
        WorkManager manager = Framework.getService(WorkManager.class);
        manager.schedule(work, false);
        Thread.sleep(100);

        manager.awaitCompletion(3000, TimeUnit.MILLISECONDS);

        pictureDoc = session.getDocument(pictureDoc.getRef());

        List<Map<String, Serializable>> views = (List<Map<String, Serializable>>) pictureDoc.getPropertyValue(
                "picture:views");
        assertEquals(digests.size(), views.size());
    }
}
