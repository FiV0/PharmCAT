package org.pharmgkb.pharmcat.haplotype;

import java.util.Map;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.pharmgkb.pharmcat.TestUtil;

import static org.junit.Assert.*;


/**
 * JUnit test for {@link VcfReader}.
 *
 * @author Mark Woon
 */
public class VcfReaderTest {

  @Test
  public void testPhasing() throws Exception {

    ImmutableSet<String> locationsOfInterest = ImmutableSet.of();
    VcfReader reader = new VcfReader(locationsOfInterest);
    Map<String, SampleAllele> alleleMap = reader.read(TestUtil.getFile("org/pharmgkb/pharmcat/haplotype/VcfReaderTest-phasing.vcf"));
    for (String chrPos : alleleMap.keySet()) {
      SampleAllele sa = alleleMap.get(chrPos);
      assertEquals(chrPos, sa.getChrPosition());
      if (sa.getChromosome().equals("chr3")) {
        // we treat homogeneous as phased
        assertTrue(sa.isPhased());
      } else {
        assertFalse(sa.isPhased());
      }
    }
  }
}
