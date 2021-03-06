package org.jdownloader.extensions.extraction;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

public interface ArchiveFactory extends ArchiveFile {

    ArrayList<ArchiveFile> createPartFileList(String pattern);

    // for (DownloadLink link1 : archive.getDownloadLinks()) {
    // link1.setProperty(ExtractionExtension.DOWNLOADLINK_KEY_EXTRACTEDPATH,
    // dl.getAbsolutePath());
    // }

    void fireExtractToChange(Archive archive);

    Collection<? extends String> getPasswordList(Archive archive);

    // archive.getFirstDownloadLink().getLinkStatus().removeStatus(LinkStatus.ERROR_POST_PROCESS);
    // archive.getFirstDownloadLink().getLinkStatus().setErrorMessage(null);
    void fireArchiveAddedToQueue(Archive archive);

    /*
     * if (archive.getFactory().getProperty(DOWNLOADLINK_KEY_EXTRACTTOPATH) !=
     * null){ return (File)
     * archive.getFactory().getProperty(DOWNLOADLINK_KEY_EXTRACTTOPATH); } if
     * (archive.getFactory() instanceof DummyDownloadLink) return new
     * File(archive.getFactory().getFileOutput()).getParentFile();
     */
    String getExtractPath(Archive archive);

    String createExtractSubPath(String path, Archive archiv);

    Archive createArchive();

    File toFile(String path);

}
