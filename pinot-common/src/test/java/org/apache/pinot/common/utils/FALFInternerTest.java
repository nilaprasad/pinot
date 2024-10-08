/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pinot.common.utils;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import java.util.Objects;
import java.util.Random;
import org.apache.pinot.spi.utils.FALFInterner;
import org.testng.Assert;
import org.testng.annotations.Test;


public class FALFInternerTest {
  @Test
  public void testInterningByteBuffers() {
    Random random = new Random(1);

    int nUniqueObjs = 1024;
    int nTotalObjs = 8 * nUniqueObjs;

    String[] allObjs = new String[nTotalObjs];

    // Create an array of objects where each object should have ~8 copies
    for (int i = 0; i < nTotalObjs; i++) {
      int next = random.nextInt(nUniqueObjs);
      allObjs[i] = Integer.toString(next);
    }

    Interner<String> exactInterner = Interners.newStrongInterner();
    Interner<String> falfInterner = new FALFInterner(nUniqueObjs);
    Interner<String> falfInternerCustomHash =
        new FALFInterner(nUniqueObjs, s -> FALFInterner.hashCode((String) s), Objects::equals);

    // Go over all objects and intern them using both exact and FALF interners
    int nHits1 = runInterning(allObjs, exactInterner, true);
    int nHits2 = runInterning(allObjs, falfInterner, true);
    int nHits3 = runInterning(allObjs, falfInternerCustomHash, true);

    // For the exact interner, we should get a hit for each object except the
    // first nUniqueObjs.
    Assert.assertEquals(nHits1, nTotalObjs - nUniqueObjs);

    // For the FALF interner, due to its fixed size and thus almost inevitable hash
    // collisions, the number of hits is smaller. Let's verify that it's not too small, though.
    Assert.assertTrue(nHits2 > (nTotalObjs - nUniqueObjs) * 0.4);

    // With the better hash function, FALF interner should have more hits
    Assert.assertTrue(nHits3 > (nTotalObjs - nUniqueObjs) * 0.6);
  }

  /**
   * Ad hoc benchmarking code. In one run the MacBook laptop, FALFInterner below performs nearly twice faster (1217 ms
   * vs 2230 ms) With custom hash function, FALFInterner's speed is about the same as the Guava interner.
   */
  @Test
  public void benchmarkingTest() {
    Random random = new Random(1);

    int nUniqueObjs = 1024;
    int nTotalObjs = 8 * nUniqueObjs;

    String[] allObjs = new String[nTotalObjs];

    Interner<String> exactInterner = Interners.newStrongInterner();
    Interner<String> falfInterner = new FALFInterner(nUniqueObjs);
    Interner<String> falfInternerCustomHash =
        new FALFInterner(nUniqueObjs, s -> FALFInterner.hashCode((String) s), Objects::equals);

    // Create an array of objects where each object should have ~8 copies
    for (int i = 0; i < nTotalObjs; i++) {
      int next = random.nextInt(nUniqueObjs);
      allObjs[i] = Integer.toString(next);
    }

    for (int j = 0; j < 3; j++) {
      long time0 = System.currentTimeMillis();
      long totNHits = 0;
      for (int i = 0; i < 10000; i++) {
        totNHits += runInterning(allObjs, exactInterner, false);
      }
      long time1 = System.currentTimeMillis();
      System.out.println("Guava interner. totNHits = " + totNHits + ", time = " + (time1 - time0));

      time0 = System.currentTimeMillis();
      totNHits = 0;
      for (int i = 0; i < 10000; i++) {
        totNHits += runInterning(allObjs, falfInterner, false);
      }
      time1 = System.currentTimeMillis();
      System.out.println("FALF interner. totNHits = " + totNHits + ", time = " + (time1 - time0));

      time0 = System.currentTimeMillis();
      totNHits = 0;
      for (int i = 0; i < 10000; i++) {
        totNHits += runInterning(allObjs, falfInternerCustomHash, false);
      }
      time1 = System.currentTimeMillis();
      System.out.println("FALF interner Custom Hash. totNHits = " + totNHits + ", time = " + (time1 - time0));
    }
  }

  private int runInterning(String[] objs, Interner<String> interner, boolean performAssert) {
    int nHits = 0;
    for (String origObj : objs) {
      String internedObj = interner.intern(origObj);
      if (performAssert) {
        Assert.assertEquals(origObj, internedObj);
      }
      if (origObj != internedObj) {
        nHits++;
      }
    }
    return nHits;
  }
}
