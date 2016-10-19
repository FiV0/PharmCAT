package org.pharmgkb.pharmcat.haplotype;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.List;
import java.util.SortedMap;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import org.pharmgkb.common.io.util.CliHelper;
import org.pharmgkb.pharmcat.definition.model.NamedAllele;
import org.pharmgkb.pharmcat.definition.model.VariantLocus;
import org.pharmgkb.pharmcat.haplotype.model.DiplotypeMatch;
import org.pharmgkb.pharmcat.haplotype.model.GeneCall;
import org.pharmgkb.pharmcat.haplotype.model.Result;


/**
 * This is the main entry point for matching {@link NamedAllele}s.
 *
 * @author Mark Woon
 */
@ThreadSafe
public class NamedAlleleMatcher {
  public static final String VERSION = "1.0.0";
  private DefinitionReader m_definitionReader;
  private ImmutableSet<String> m_locationsOfInterest;
  private boolean m_assumeReferenceInDefinitions;
  private boolean m_topCandidateOnly;


  /**
   * Default constructor.
   * This will only call the top candidate(s) and assume reference.
   */
  public NamedAlleleMatcher(@Nonnull DefinitionReader definitionReader) {
    this(definitionReader, true, false);
  }

  /**
   * Constructor.
   *
   * @param topCandidateOnly true if only top candidate(s) should be called, false to call all possible candidates
   * @param assumeReference true if missing alleles in definitions should be treated as reference, false otherwise
   */
  public NamedAlleleMatcher(@Nonnull DefinitionReader definitionReader, boolean assumeReference, boolean topCandidateOnly) {

    Preconditions.checkNotNull(definitionReader);
    m_definitionReader = definitionReader;
    m_locationsOfInterest = calculateLocationsOfInterest(m_definitionReader);
    m_assumeReferenceInDefinitions = assumeReference;
    m_topCandidateOnly = topCandidateOnly;
  }


  public static void main(String[] args) {

    try {
      CliHelper cliHelper = new CliHelper(MethodHandles.lookup().lookupClass())
          .addOption("d", "definition-dir", "directory of allele definition files", true, "d")
          .addOption("vcf", "vcf-in", "VCF file", true, "vcf")
          .addOption("json", "json-out", "file to save results to (in JSON format)", false, "json")
          .addOption("html", "html-out", "file to save results to (in HTML format)", false, "html")
          .addOption("ref", "reference", "assume reference for scoring missing positions");

      if (!cliHelper.parse(args)) {
        System.exit(1);
      }

      Path definitionDir = cliHelper.getValidDirectory("d", false);
      Path vcfFile = cliHelper.getValidFile("vcf", false);

      DefinitionReader definitionReader = new DefinitionReader();
      definitionReader.read(definitionDir);
      if (definitionReader.getGenes().size() == 0) {
        System.out.println("Did not find any allele definitions at " + definitionDir);
        System.exit(1);
      }

      NamedAlleleMatcher namedAlleleMatcher;

      if (cliHelper.hasOption("ref")) {
        System.out.println("Reference assumed for scoring missing positions");
        namedAlleleMatcher = new NamedAlleleMatcher(definitionReader,true, false);
      } else {
        System.out.println("Reference not assumed for scoring missing positions");
        namedAlleleMatcher = new NamedAlleleMatcher(definitionReader);
      }

      Result result = namedAlleleMatcher.call(vcfFile);

      ResultSerializer resultSerializer = new ResultSerializer();
      resultSerializer.alwaysShowUnmatchedHaplotypes(true);

      if (cliHelper.hasOption("json")) {
        resultSerializer.toJson(result, cliHelper.getPath("json"));
      }
      if (cliHelper.hasOption("html")) {
        resultSerializer.toHtml(result, cliHelper.getPath("html"));
      }
      if (cliHelper.isVerbose()){
        for (GeneCall geneCalls : result.getGeneCalls()) {
          System.out.println("Gene: " + geneCalls.getGene());
          System.out.print("Diplotypes: ");
          geneCalls.getDiplotypes().forEach(f-> System.out.print(f.toString() +"\t(score:"+ f.getScore() + ") "));
          System.out.print("\nMissing positions (" + geneCalls.getMatchData().getMissingPositions().size() + " out of " +
              (geneCalls.getMatchData().getMissingPositions().size() +  geneCalls.getVariants().size()) + "): ");
          geneCalls.getMatchData().getMissingPositions().forEach(f-> System.out.print(geneCalls.getChromosome() + " " + f.getVcfPosition() + "   "));
          System.out.print("\nUncallable (" + geneCalls.getUncallableHaplotypes().size()  + "): " );
          geneCalls.getUncallableHaplotypes().forEach(f->System.out.print(f + "  "));
          System.out.println("\n");
        }

      }

    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }


  /**
   * Builds a new VCF reader for the given file.
   */
  VcfReader buildVcfReader(Path vcfFile) throws IOException {
    return new VcfReader(m_locationsOfInterest, vcfFile);
  }


  /**
   * Collects all locations of interest (i.e. positions necessary to make a haplotype call).
   *
   * @return a set of {@code <chr:position>} Strings
   */
  private static ImmutableSet<String> calculateLocationsOfInterest(DefinitionReader definitionReader) {

    ImmutableSet.Builder<String> setBuilder = ImmutableSet.builder();
    for (String gene : definitionReader.getGenes()) {
      VariantLocus[] variants = definitionReader.getPositions(gene);
      String chromosome = definitionReader.getDefinitionFile(gene).getChromosome();
      // map variant to chr:position
      for (VariantLocus v : variants) {
        setBuilder.add(chromosome + ":" + v.getVcfPosition());
      }
    }
    return setBuilder.build();
  }


  /**
   * Calls diplotypes for the given VCF file for all genes for which a definition exists.
   */
  public Result call(@Nonnull Path vcfFile) throws IOException {

    VcfReader vcfReader = buildVcfReader(vcfFile);
    SortedMap<String, SampleAllele> alleles = vcfReader.getAlleleMap();
    ResultBuilder resultBuilder = new ResultBuilder(m_definitionReader)
        .forFile(vcfFile);
    // call haplotypes
    for (String gene : m_definitionReader.getGenes()) {
      MatchData data = initializeCallData(alleles, gene);

      List<DiplotypeMatch> matches = null;
      if (data.getNumSampleAlleles() > 0) {
        matches = callDiplotypes(data);
      }
      resultBuilder.gene(gene, data, matches);
    }
    return resultBuilder.build();
  }


  /**
   * Initializes data required to call a diplotype.
   *
   * @param alleleMap map of {@link SampleAllele}s from VCF
   */
  private @Nonnull MatchData initializeCallData(SortedMap<String, SampleAllele> alleleMap, String gene) {

    // grab SampleAlleles for all positions related to current gene
    MatchData data = new MatchData(alleleMap, m_definitionReader.getDefinitionFile(gene).getChromosome(),
        m_definitionReader.getPositions(gene));
    if (data.getNumSampleAlleles() == 0) {
      return data;
    }
    // handle missing positions (if any)
    data.marshallHaplotypes(m_definitionReader.getHaplotypes(gene));

    if (m_assumeReferenceInDefinitions) {
      data.defaultMissingAllelesToReference();
    }

    data.generateSamplePermutations();
    return data;
  }


  /**
   * Calls the possible diplotypes for a single gene.
   *
   */
  protected List<DiplotypeMatch> callDiplotypes(MatchData data) {

    // find matched pairs
    List<DiplotypeMatch> pairs = new DiplotypeMatcher(data)
        .compute();
    if (m_topCandidateOnly) {
      if (pairs.size() > 1) {
        int topScore = pairs.get(0).getScore();
        pairs = pairs.stream()
            .filter(dm -> dm.getScore() == topScore)
            .collect(Collectors.toList());
      }
    }
    return pairs;
  }
}
