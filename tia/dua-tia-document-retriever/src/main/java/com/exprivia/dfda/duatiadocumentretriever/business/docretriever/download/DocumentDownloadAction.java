package com.exprivia.dfda.duatiadocumentretriever.business.docretriever.download;

import java.io.File;
import java.util.List;

import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class DocumentDownloadAction {
    private final static String DIR_COMPONENT_SEPARATOR = "-";

    @NonNull
    private String groupName;

    @NonNull
    private String id;

    @NonNull
    private String downloadDriver;

    @NonNull
    private List<String> doi;

    @NonNull
    private List<String> documentUrl;

    private int minimumRequired;

    private String repositoryCredentials;

    @NonNull
    private File localGroupPath = null;

    @NonNull
    private File localDownloadPath = null;

    private String dirEventId = null;

    public DocumentDownloadAction(
        String groupName,
        String id, String downloadDriver, 
        List<String> dois, List<String> documentUrls, 
        int minimumRequired, String repositoryCredentials, 
        String basePath) {

        this.groupName = groupName;
        this.id = id;
        this.downloadDriver = downloadDriver;
        this.doi = dois;
        this.documentUrl = documentUrls;
        this.minimumRequired = minimumRequired;
        this.repositoryCredentials = repositoryCredentials;

        if (basePath != null) {
            this.localGroupPath = new File(basePath, groupName);
            this.localDownloadPath = this.localGroupPath;
        }
    }

    public DocumentDownloadAction(
        String groupName,
        String id, String downloadDriver, 
        List<String> dois, List<String> documentUrls, 
        int minimumRequired, String repositoryCredentials, 
        String basePath, String repoNameForSubdirCreation, String missionNameForSubdirCreation) {

        this(
            groupName,
            id, downloadDriver, 
            dois, documentUrls,
            minimumRequired, repositoryCredentials, 
            basePath
        );

        // add a path portion to create a unique temporary directory
        // containing reponame, mission and docid
        dirEventId = 
            sanitizeDirectoryComponent(repoNameForSubdirCreation) + DIR_COMPONENT_SEPARATOR + 
            sanitizeDirectoryComponent(missionNameForSubdirCreation) + DIR_COMPONENT_SEPARATOR + 
            sanitizeDirectoryComponent(id);

        localDownloadPath = new File(localGroupPath, dirEventId);
    }

    public String getDirEventId() {
        return groupName + File.separatorChar + dirEventId;
    }

    private static String sanitizeDirectoryComponent(String v) {
        return v.replaceAll("[/:. -]", "_");
    }

    public void createLocalDirectory() {
        if (!localGroupPath.exists()) {
            log.info("creating local group path: {}", localGroupPath.getAbsolutePath());
            localGroupPath.mkdir();
        }

        // localDownloadPath and localGroupPath are equals in test mode,
        // when the repo/mission/id dirEventId combination is not specified
        if (!localDownloadPath.equals(localGroupPath)) {
            if (localDownloadPath.exists()) {
            log.warn("directory already exists: {}", localDownloadPath.getAbsolutePath());
            } else {
                localDownloadPath.mkdir();
            }
        }
    }

}
