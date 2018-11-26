import omar.disk.cleanup.DiskCleanupConfig
// Place your Spring DSL code here
beans = {
   diskCleanupConfig(DiskCleanupConfig)
   diskCleanupVolumeConverter(DiskCleanupConfig.DiskCleanupVolumeConverter)
   diskCleanupDbInfoConverter(DiskCleanupConfig.DiskCleanupDbInfoConverter)

}
