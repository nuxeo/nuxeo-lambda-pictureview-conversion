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
package org.nuxeo.ecm.lambda.image.conversion.test;

import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.lambda.core.LambdaInputPreprocessor;
import org.nuxeo.ecm.lambda.core.service.LambdaService;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertNotNull;

@RunWith(FeaturesRunner.class)
@Features({ CoreFeature.class, PlatformFeature.class })
@Deploy({
        "org.nuxeo.lambda.integration.core",
        "org.nuxeo.ecm.platform.picture.core",
        "org.nuxeo.ecm.core.cache",
        "org.nuxeo.ecm.automation.core",
        "org.nuxeo.lambda.image.conversion"
})
public class TestLambdaIntegration {

    @Test
    public void shouldScheduleCall() throws Exception {
        LambdaService service = Framework.getService(LambdaService.class);
        assertNotNull(service);

        Map<String, Serializable> map = new HashMap<>();
        map.put("testKey", "testValue");

        LambdaInputPreprocessor preprocessor = new LambdaInputPreprocessor(map);

        JSONObject object = service.scheduleCall("nuxeo-lambda", null, preprocessor);
        assertNotNull(object);
    }
}
