/*
 * Copyright 2014-2016 Netflix, Inc.
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
 */

package com.netflix.gradle.plugins.daemon

import groovy.text.GStringTemplateEngine
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class TemplateHelper {
    static final Logger logger = LoggerFactory.getLogger(TemplateHelper.class)

    private final GStringTemplateEngine engine = new GStringTemplateEngine()

    File destDir
    String templatePrefix

    TemplateHelper(File destDir, String templatePrefix) {
        this.destDir = destDir
        this.templatePrefix = templatePrefix
    }

    File generateFile(String templateName, Map context) {
        logger.info("Generating ${templateName} file...")
        context.each { key, value ->
            if (value == null) {
                throw new IllegalArgumentException("Context key $key has a null value")
            }
        }
        def template = getClass().getResourceAsStream("${templatePrefix}/${templateName}.tpl").newReader()
        def content = engine.createTemplate(template).make(context).toString()
        def contentFile = new File(destDir, templateName)
        destDir.mkdirs()
        contentFile.text = content
        return contentFile
    }

}
