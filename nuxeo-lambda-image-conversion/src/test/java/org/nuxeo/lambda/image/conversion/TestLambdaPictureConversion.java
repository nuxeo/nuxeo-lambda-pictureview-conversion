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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.lambda.image.conversion.common.LambdaFeature;
import org.nuxeo.lambda.image.conversion.common.MemLambdaCaller;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.transaction.TransactionHelper;

import javax.inject.Inject;
import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

@RunWith(FeaturesRunner.class)
@Features({ LambdaFeature.class })
@Deploy("org.nuxeo.lambda.image.conversion.test:OSGI-INF/lambda-picture-config-override-contrib.xml")
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

    @Test(timeout = 30 * 1000)
    @SuppressWarnings("unchecked")
    public void testLambdaPictureConversion() throws Exception {
        DocumentModel pictureDoc = session.createDocumentModel("/", "MyPicture", "File");
        pictureDoc.addFacet("Picture");

        File image = new File(getClass().getClassLoader().getResource("binaries/nuxeo.png").getFile());
        Blob blob = new FileBlob(image);
        pictureDoc.setPropertyValue("file:content", (Serializable) blob);

        TransactionHelper.startTransaction();

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
        List<Map<String, Serializable>> pictureViews = (List<Map<String, Serializable>>) pictureDoc.getPropertyValue(
                "picture:views");
        assertNotNull(pictureViews);
        assertEquals(3, pictureViews.size());
    }
}
