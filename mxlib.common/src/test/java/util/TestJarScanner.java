/*
 * Copyright (c) 2018-2021 Karlatemp. All rights reserved.
 * @author Karlatemp <karlatemp@vip.qq.com> <https://github.com/Karlatemp>
 *
 * MXLib/MXLib.mxlib-common.test/TestJarScanner.java
 *
 * Use of this source code is governed by the MIT license that can be found via the following link.
 *
 * https://github.com/Karlatemp/MxLib/blob/master/LICENSE
 */

package util;

import io.github.karlatemp.mxlib.common.utils.BeanManagers;
import io.github.karlatemp.mxlib.common.utils.SimpleJarScanner;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;

public class TestJarScanner {
    @Test
    void test() throws Throwable {
        var scanner = new SimpleJarScanner(BeanManagers.newStandardManager());
        scanner.scan(new File("."), new ArrayList<>()).forEach(System.out::println);
    }


}
