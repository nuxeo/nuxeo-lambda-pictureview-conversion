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
package org.nuxeo.lambda.image.conversion.common;

import java.io.Serializable;
import java.util.Map;

import org.nuxeo.ecm.platform.picture.api.ImageInfo;

import com.fasterxml.jackson.databind.JsonNode;

public class ImageProperties implements Serializable {

    public static final String IMAGE_NAME = "name";

    public static final String IMAGE_DIGEST = "digest";

    public static final String IMAGE_WIDTH = "width";

    public static final String IMAGE_HEIGHT = "height";

    public static final String IMAGE_LENGTH = "length";

    private String name;

    private String digest;

    private Integer width;

    private Integer height;

    private Integer length;

    public ImageProperties() {
    }

    public ImageProperties(JsonNode object) {
        this.name = object.get(IMAGE_NAME).asText();
        this.digest = object.get(IMAGE_DIGEST).asText();
        this.width = object.get(IMAGE_WIDTH).asInt();
        this.height = object.get(IMAGE_HEIGHT).asInt();
        this.length = object.get(IMAGE_LENGTH).asInt();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDigest() {
        return digest;
    }

    public void setDigest(String digest) {
        this.digest = digest;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public Integer getLength() {
        return length;
    }

    public void setLength(Integer length) {
        this.length = length;
    }

    public Map<String, Serializable> toMap(String filename) {
        return new ImageInfo(width.toString(), height.toString(), "png", filename).toMap();

    }

    @Override
    public String toString() {
        return "ImageProperties {" + "name='" + name + '\'' + ", digest='" + digest + '\'' + ", width=" + width
                + ", height=" + height + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImageProperties)) {
            return false;
        }

        ImageProperties imageProperties = (ImageProperties) o;

        return name.equals(imageProperties.name) && digest.equals(imageProperties.digest)
                && width.equals(imageProperties.width) && height.equals(imageProperties.height);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + digest.hashCode();
        result = 31 * result + width.hashCode();
        result = 31 * result + height.hashCode();
        return result;
    }
}
