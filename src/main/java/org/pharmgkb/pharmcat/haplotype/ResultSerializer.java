package org.pharmgkb.pharmcat.haplotype;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.pharmgkb.pharmcat.definition.model.NamedAllele;
import org.pharmgkb.pharmcat.definition.model.VariantLocus;
import org.pharmgkb.pharmcat.haplotype.model.DiplotypeMatch;
import org.pharmgkb.pharmcat.haplotype.model.GeneCall;
import org.pharmgkb.pharmcat.haplotype.model.HaplotypeMatch;
import org.pharmgkb.pharmcat.haplotype.model.Result;
import org.pharmgkb.pharmcat.haplotype.model.Variant;


/**
 * Serializer/Deserializer for {@link Result}.
 *
 * @author Mark Woon
 */
public class ResultSerializer {
  private static final Gson sf_gson = new GsonBuilder().serializeNulls().excludeFieldsWithoutExposeAnnotation()
      .setPrettyPrinting().create();
  private boolean m_alwaysShowUnmatchedHaplotypes;
  private SimpleDateFormat m_dateFormat = new SimpleDateFormat("MM/dd/yy");


  public ResultSerializer() {
  }


  public ResultSerializer alwaysShowUnmatchedHaplotypes(boolean alwaysShowUnmatchedHaplotypes) {
    m_alwaysShowUnmatchedHaplotypes = alwaysShowUnmatchedHaplotypes;
    return this;
  }



  public ResultSerializer toJson(@Nonnull Result result, @Nonnull Path jsonFile) throws IOException {
    Preconditions.checkNotNull(result);
    Preconditions.checkNotNull(jsonFile);
    Preconditions.checkArgument(jsonFile.toString().endsWith(".json"));

    try (BufferedWriter writer = Files.newBufferedWriter(jsonFile, StandardCharsets.UTF_8)) {
      writer.write(sf_gson.toJson(result));
    }
    return this;
  }


  public Result fromJson(@Nonnull Path jsonFile) throws IOException {
    Preconditions.checkNotNull(jsonFile);
    Preconditions.checkArgument(jsonFile.toString().endsWith(".json"));
    Preconditions.checkArgument(Files.isRegularFile(jsonFile));

    try (BufferedReader reader = Files.newBufferedReader(jsonFile, StandardCharsets.UTF_8)) {
      return sf_gson.fromJson(reader, Result.class);
    }
  }



  public ResultSerializer toHtml(@Nonnull Result result, @Nonnull Path htmlFile) throws IOException {
    Preconditions.checkNotNull(result);
    Preconditions.checkNotNull(htmlFile);
    Preconditions.checkArgument(htmlFile.toString().endsWith(".html"));

    StringBuilder builder = new StringBuilder();
    for (GeneCall call : result.getGeneCalls()) {
      MatchData matchData = call.getMatchData();

      builder.append("<h3>")
          .append("<a name=\"" + call.getGene() + "\">" + call.getGene() +" </a>"  )
          .append("</h3>");

      builder.append("<ul>");
      for (DiplotypeMatch diplotype : call.getDiplotypes()) {
        builder.append("<li>")
            .append(diplotype.getName())
            .append(" (")
            .append(diplotype.getScore())
            .append(")</li>");
      }
      builder.append("</ul>");

      builder.append("<table class=\"table table-striped table-hover table-condensed\">");
      // position
      builder.append("<tr>");
      builder.append("<th>Definition Position</th>");
      for (Variant v : call.getVariants()) {
        builder.append("<th>")
            .append(v.getPosition())
            .append("</th>");
      }
      builder.append("</tr>");
      // rsid
      builder.append("<tr>");
      builder.append("<th></th>");
      for (Variant v : call.getVariants()) {
        builder.append("<th>");
        if (v.getRsid() != null) {
          builder.append(v.getRsid());
        }
        builder.append("</th>");
      }
      builder.append("</tr>");
      // VCF position
      builder.append("<tr>");
      builder.append("<th>VCF Position</th>");
      for (Variant v : call.getVariants()) {
        builder.append("<th>")
            .append(v.getVcfPosition())
            .append("</th>");
      }
      builder.append("</tr>");
      // sample
      builder.append("<tr>");
      builder.append("<th>VCF REF,ALTs</th>");
      for (Variant v : call.getVariants()) {
        builder.append("<th>")
            .append(v.getVcfAlleles())
            .append("</th>");
      }
      builder.append("</tr>");

      builder.append("<tr class=\"success\">");
      builder.append("<th>VCF Call</th>");
      for (Variant v : call.getVariants()) {
        builder.append("<th>")
            .append(v.getVcfCall())
            .append("</th>");
      }
      builder.append("</tr>");

      Set<String> matchedHaplotypeNames = new HashSet<>();
      if (call.getHaplotypes().size() > 0) {
        for (HaplotypeMatch hm : call.getHaplotypes()) {
          matchedHaplotypeNames.add(hm.getHaplotype().getName());
          printAllele(builder, hm.getHaplotype().getName(), hm.getHaplotype().getPermutations().pattern(), "info");
          for (String seq : hm.getSequences()) {
            printAllele(builder, null, seq, null);
          }
        }
      }
      if (m_alwaysShowUnmatchedHaplotypes || matchedHaplotypeNames.size() == 0) {
        for (NamedAllele haplotype : matchData.getHaplotypes()) {
          if (!matchedHaplotypeNames.contains(haplotype.getName())) {
            printAllele(builder, haplotype.getName(), haplotype.getPermutations().pattern(), "danger");
          }
        }
      }

      builder.append("</table>");

      if (matchData.getMissingPositions().size() > 0) {
        builder.append("<p>There ");
        if (matchData.getMissingPositions().size() > 1) {
          builder.append("were ");
        } else {
          builder.append("was ");
        }
        builder.append(matchData.getMissingPositions().size())
            .append(" missing positions from the VCF file:</p>")
            .append("<ul>");
        for (VariantLocus variant : matchData.getMissingPositions()) {
          builder.append("<li>")
              .append(variant.getVcfPosition())
              .append(" (")
              .append(variant.getChromosomeHgvsName())
              .append(")</li>");
        }
        builder.append("</ul>");

        if (call.getUncallableHaplotypes().size() > 0) {
          builder.append("<p>The following haplotype(s) were eliminated from consideration:</p>")
              .append("<ul>");
          for (String name : call.getUncallableHaplotypes()) {
            builder.append("<li>")
                .append(name)
                .append("</li>");
          }
          builder.append("</ul>");
        }

        if (call.getHaplotypes().size() > 0) {
          builder.append("<p>The following haplotypes were called even though tag positions were missing:</p>")
              .append("<ul>");
          for (HaplotypeMatch hm : call.getHaplotypes()) {
            if (hm.getHaplotype().getMissingPositions().size() > 0) {
              builder.append("<li>Called ")
                  .append(hm.getName())
                  .append(" without ")
                  .append(hm.getHaplotype().getMissingPositions().stream()
                      .map(VariantLocus::getChromosomeHgvsName)
                      .collect(Collectors.joining(", ")))
                  .append("</li>");
            }
          }
          builder.append("</ul>");
        }
      }
    }

    System.out.println("Printing to " + htmlFile);
    try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(htmlFile, StandardCharsets.UTF_8))) {
      Map<String, String> varMap = new HashMap<>();
      varMap.put("title", "PharmCAT Allele Call Report for " + result.getMetadata().getInputFilename());
      varMap.put("content", builder.toString());
      varMap.put("timestamp", m_dateFormat.format(new Date()));
      StrSubstitutor sub = new StrSubstitutor(varMap);
      String template = IOUtils.toString(getClass().getResourceAsStream("template.html"));
      writer.println(sub.replace(template));
    }
    return this;
  }


  private void printAllele(@Nonnull StringBuilder builder, @Nullable String name, @Nonnull String allele,
      @Nullable String rowClass) {

    SortedSet<Variant> variants = new TreeSet<>();
    for (String posAllele : allele.split(";")) {
      String[] parts = posAllele.split(":");
      String a = parts[1];
      if (a.equals(".?")) {
        a = "";
      }
      int vcfPosition = Integer.parseInt(parts[0]);
      variants.add(new Variant(-1, null, a, vcfPosition, ""));
    }

    builder.append("<tr");
    if (rowClass != null) {
      builder.append(" class=\"")
          .append(rowClass)
          .append("\"");
    }
    builder.append("><th>");
    if (name != null) {
      builder.append(name);
    }
    builder.append("</th>");

    for (Variant variant : variants) {
      String vcfCall = variant.getVcfCall();
      if (vcfCall.contains("\\")) {
        vcfCall = vcfCall.replaceAll("\\\\", "");
      }
      builder.append("<td>");
      if (name == null) {
        builder.append(vcfCall);
      } else {
        builder.append("<b>")
            .append(vcfCall)
            .append("</b>");
      }
      builder.append("</td>");
    }

    builder.append("</tr>");
  }
}
