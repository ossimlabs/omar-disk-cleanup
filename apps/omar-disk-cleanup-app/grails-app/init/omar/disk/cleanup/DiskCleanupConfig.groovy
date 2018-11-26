package omar.disk.cleanup

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding
import org.springframework.core.convert.converter.Converter
import groovy.transform.ToString

/**
 * Created by sbortman on 12/24/16.
 */
@ConfigurationProperties(prefix="omar.disk.cleanup",ignoreInvalidFields=true)
@ToString(includeNames=true)
class DiskCleanupConfig
{
   Boolean deleteStaleEntries
   Boolean deleteStaleFiles
   Boolean dryRun
   List<Volume> volumes;

  @ToString(includeNames=true)
  static class Volume {
     String  repository
     Double  minDiskLimitPercent
     Double  maxDiskLimitPercent
     Boolean enableScan
     DbInfo  dbInfo
     String  stagerUrl
  }
  @ToString(includeNames=true)
  static class DbInfo {
    HashMap<String,String> dataSource
    String                 tableName
    String                 filenameColumn
    String                 lruDateColumn
  }

  @ConfigurationPropertiesBinding
  static class DiskCleanupDbInfoConverter implements Converter<Map<String, Object>, DbInfo>
  {
    @Override
    DbInfo convert(Map<String, Object> map)
    {
      return new DbInfo( map )
    }
  }

  @ConfigurationPropertiesBinding
  static class DiskCleanupVolumeConverter implements Converter<Map<String, String>, Volume>
  {
    @Override
    Volume convert(Map<String, String> map)
    {
      return new Volume( map)
    }
  }
}
