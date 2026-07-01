package com.cooperativesolutionism.nmsci.dto;

/**
 * 区块 .dat 文件存储利用率快照。
 */
public class StorageStatusDTO {

    private String datDirectory;

    private int datFileCount;

    private String currentDatFileName;

    private long currentDatFileSizeBytes;

    private long totalDatBytes;

    private long datMaxSizePerFileBytes;

    private double currentDatUtilizationPct;

    public static StorageStatusDTO from(
            String datDirectory,
            int datFileCount,
            String currentDatFileName,
            long currentDatFileSizeBytes,
            long totalDatBytes,
            long datMaxSizePerFileBytes
    ) {
        StorageStatusDTO dto = new StorageStatusDTO();
        dto.setDatDirectory(datDirectory);
        dto.setDatFileCount(datFileCount);
        dto.setCurrentDatFileName(currentDatFileName);
        dto.setCurrentDatFileSizeBytes(currentDatFileSizeBytes);
        dto.setTotalDatBytes(totalDatBytes);
        dto.setDatMaxSizePerFileBytes(datMaxSizePerFileBytes);
        dto.setCurrentDatUtilizationPct(
                datMaxSizePerFileBytes > 0 ? currentDatFileSizeBytes * 100.0 / datMaxSizePerFileBytes : 0.0
        );
        return dto;
    }

    public String getDatDirectory() {
        return datDirectory;
    }

    public void setDatDirectory(String datDirectory) {
        this.datDirectory = datDirectory;
    }

    public int getDatFileCount() {
        return datFileCount;
    }

    public void setDatFileCount(int datFileCount) {
        this.datFileCount = datFileCount;
    }

    public String getCurrentDatFileName() {
        return currentDatFileName;
    }

    public void setCurrentDatFileName(String currentDatFileName) {
        this.currentDatFileName = currentDatFileName;
    }

    public long getCurrentDatFileSizeBytes() {
        return currentDatFileSizeBytes;
    }

    public void setCurrentDatFileSizeBytes(long currentDatFileSizeBytes) {
        this.currentDatFileSizeBytes = currentDatFileSizeBytes;
    }

    public long getTotalDatBytes() {
        return totalDatBytes;
    }

    public void setTotalDatBytes(long totalDatBytes) {
        this.totalDatBytes = totalDatBytes;
    }

    public long getDatMaxSizePerFileBytes() {
        return datMaxSizePerFileBytes;
    }

    public void setDatMaxSizePerFileBytes(long datMaxSizePerFileBytes) {
        this.datMaxSizePerFileBytes = datMaxSizePerFileBytes;
    }

    public double getCurrentDatUtilizationPct() {
        return currentDatUtilizationPct;
    }

    public void setCurrentDatUtilizationPct(double currentDatUtilizationPct) {
        this.currentDatUtilizationPct = currentDatUtilizationPct;
    }
}
