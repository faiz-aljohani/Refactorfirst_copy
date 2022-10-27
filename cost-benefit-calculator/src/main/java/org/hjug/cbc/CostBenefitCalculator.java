package org.hjug.cbc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.hjug.git.ChangePronenessRanker;
import org.hjug.git.GitLogReader;
import org.hjug.git.RepositoryLogReader;
import org.hjug.git.ScmLogInfo;
import org.hjug.metrics.*;

@Slf4j
public class CostBenefitCalculator {

    public List<RankedDisharmony> calculateCostBenefitValues(String repositoryPath) {

        RepositoryLogReader repositoryLogReader = new GitLogReader();
        Repository repository = null;
        log.info("Initiating Cost Benefit calculation");
        try {
            repository = repositoryLogReader.gitRepository(new File(repositoryPath));
        } catch (IOException e) {
            log.error("Failure to access Git repository", e);
        }

        Map<String, ByteArrayOutputStream> filesToScan = getFilesToScan(repositoryLogReader, repository);

        List<GodClass> godClasses = getGodClasses(filesToScan);
        List<CBOClass> cboClasses = getCBOClasses(filesToScan);

        List<ScmLogInfo> scmLogInfos = getRankedChangeProneness(repositoryLogReader, repository, godClasses);

        Map<String, ScmLogInfo> rankedLogInfosByPath =
                scmLogInfos.stream().collect(Collectors.toMap(ScmLogInfo::getPath, logInfo -> logInfo, (a, b) -> b));

        List<RankedDisharmony> rankedDisharmonies = new ArrayList<>();
        for (GodClass godClass : godClasses) {
            rankedDisharmonies.add(new RankedDisharmony(godClass, rankedLogInfosByPath.get(godClass.getFileName())));
        }

        return rankedDisharmonies;
    }

    List<ScmLogInfo> getRankedChangeProneness(
            RepositoryLogReader repositoryLogReader, Repository repository, List<GodClass> godClasses) {
        List<ScmLogInfo> scmLogInfos = new ArrayList<>();
        log.info("Calculating Change Proneness for each God Class");
        for (GodClass godClass : godClasses) {
            String path = godClass.getFileName();
            ScmLogInfo scmLogInfo = null;
            try {
                scmLogInfo = repositoryLogReader.fileLog(repository, path);
            } catch (GitAPIException | IOException e) {
                log.error("Error reading Git repository contents", e);
            }

            scmLogInfos.add(scmLogInfo);
        }

        ChangePronenessRanker changePronenessRanker = new ChangePronenessRanker(repository, repositoryLogReader);
        changePronenessRanker.rankChangeProneness(scmLogInfos);
        return scmLogInfos;
    }

    private List<GodClass> getGodClasses(Map<String, ByteArrayOutputStream> filesToScan) {
        PMDGodClassRuleRunner ruleRunner = new PMDGodClassRuleRunner();

        log.info("Identifying God Classes from files in repository");
        List<GodClass> godClasses = new ArrayList<>();
        for (Map.Entry<String, ByteArrayOutputStream> entry : filesToScan.entrySet()) {
            String filePath = entry.getKey();
            ByteArrayOutputStream value = entry.getValue();

            ByteArrayInputStream inputStream = new ByteArrayInputStream(value.toByteArray());
            Optional<GodClass> godClassOptional = ruleRunner.runGodClassRule(filePath, inputStream);
            godClassOptional.ifPresent(godClasses::add);
        }

        GodClassRanker godClassRanker = new GodClassRanker();
        godClassRanker.rankGodClasses(godClasses);
        return godClasses;
    }

    private List<CBOClass> getCBOClasses(Map<String, ByteArrayOutputStream> filesToScan) {

        CBORuleRunner ruleRunner = new CBORuleRunner();
        
        log.info("Identifying highly coupled classes from files in repository");
        List<CBOClass> cboClasses = new ArrayList<>();
        for (Map.Entry<String, ByteArrayOutputStream> entry : filesToScan.entrySet()) {
            String filePath = entry.getKey();
            ByteArrayOutputStream value = entry.getValue();

            ByteArrayInputStream inputStream = new ByteArrayInputStream(value.toByteArray());
            Optional<CBOClass> godClassOptional = ruleRunner.runCBOClassRule(filePath, inputStream);
            godClassOptional.ifPresent(cboClasses::add);
        }

        return cboClasses;
    }

    private Map<String, ByteArrayOutputStream> getFilesToScan(RepositoryLogReader repositoryLogReader, Repository repository) {
        Map<String, ByteArrayOutputStream> filesToScan = new HashMap<>();
        try {
            filesToScan = repositoryLogReader.listRepositoryContentsAtHEAD(repository);
        } catch (IOException e) {
            log.error("Error reading Git repository contents", e);
        }
        return filesToScan;
    }
}
