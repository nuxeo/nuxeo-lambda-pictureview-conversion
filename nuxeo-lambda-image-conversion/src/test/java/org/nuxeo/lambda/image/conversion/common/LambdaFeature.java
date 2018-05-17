/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
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

import static org.nuxeo.lambda.image.conversion.common.Constants.BUCKET_PREFIX_PROP;
import static org.nuxeo.lambda.image.conversion.common.Constants.BUCKET_PROP;

import java.util.Properties;

import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.SimpleFeature;

@Features(PlatformFeature.class)
@Deploy({
        "org.nuxeo.lambda.core", "org.nuxeo.lambda.image.conversion",
        "org.nuxeo.ecm.platform.picture.api", "org.nuxeo.ecm.platform.picture.core",
        "org.nuxeo.ecm.platform.tag",
        "org.nuxeo.ecm.core.cache",
        "org.nuxeo.ecm.automation.core"
})
public class LambdaFeature extends SimpleFeature {

    public LambdaFeature() {
    }

    @Override
    public void beforeRun(FeaturesRunner runner) throws Exception {
        super.beforeRun(runner);
        Properties properties = Framework.getProperties();
        properties.put(BUCKET_PROP, "test");
        properties.put(BUCKET_PREFIX_PROP, "binaries");
    }
}
