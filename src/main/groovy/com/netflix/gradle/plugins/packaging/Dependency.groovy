/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.gradle.plugins.packaging

import groovy.transform.EqualsAndHashCode
import org.redline_rpm.header.Flags

@EqualsAndHashCode
class Dependency implements Serializable {
    private static final long serialVersionUID = 5707700441069141432L;

    String packageName
    String version
    int flag = 0
    Dependency alternative = null

    Dependency(String packageName, String version, int flag=0) {
        assert !packageName.contains(','), "Package name ($packageName) can not include commas"
        this.packageName = packageName
        this.version = version
        this.flag = flag
    }

    Dependency or(String packageName, String version='', int flag=0) {
        alternative = new Dependency(packageName, version, flag)
        alternative
    }

    String toDebString() {
        def signMap = [
            (Flags.GREATER|Flags.EQUAL): '>=',
            (Flags.LESS|Flags.EQUAL):    '<=',
            (Flags.EQUAL):               '=',
            (Flags.GREATER):             '>>',
            (Flags.LESS):                '<<'
        ]

        def depStr = this.packageName
        if (this.flag && this.version) {
            def sign = signMap[this.flag]
            if (sign==null) {
                throw new IllegalArgumentException()
            }
            depStr += " (${sign} ${this.version})"
        } else if (this.version) {
            depStr += " (${this.version})"
        }
        if (alternative) {
            depStr += " | " + alternative.toDebString()
        }
        depStr
    }
}
