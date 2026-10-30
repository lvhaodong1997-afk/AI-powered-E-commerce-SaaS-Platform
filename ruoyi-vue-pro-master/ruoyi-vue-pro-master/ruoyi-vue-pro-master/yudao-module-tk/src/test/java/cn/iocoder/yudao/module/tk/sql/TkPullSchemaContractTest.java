package cn.iocoder.yudao.module.tk.sql;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class TkPullSchemaContractTest {
 @Test void upgradeIsAdditiveGuardedAndFreshDefinitionsCarryDueIndexes() throws Exception {
  String upgrade=read("tk_tiktok_pull_from_url_upgrade_mysql.sql");
  assertTrue(upgrade.contains("information_schema.COLUMNS"));
  assertTrue(upgrade.contains("information_schema.STATISTICS"));
  assertFalse(upgrade.toUpperCase().contains("DROP COLUMN"));
  assertFalse(upgrade.contains("UPDATE tk_"));
  for(String field:new String[]{"transfer_policy","pull_url_cipher","next_poll_time","downloaded_bytes","transfer_source","source_file_size","init_phase","init_started_at"}) assertTrue(upgrade.contains("`"+field+"`"),field);
  for(String resource:new String[]{"tk_tiktok_open_api_mysql.sql","tk_tiktok_publish_center_mysql.sql"}) {
   String fresh=read(resource);assertTrue(fresh.contains("`transfer_policy` varchar(32) DEFAULT 'LEGACY'"));
   assertTrue(fresh.contains("`idx_pull_due` (`status`, `next_poll_time`, `id`)"));
   assertTrue(fresh.contains("tk_tiktok_pull_from_url_upgrade_mysql.sql"));
  }
 }
 private String read(String name) throws Exception {
  try(InputStream in=getClass().getResourceAsStream("/sql/"+name)) {assertNotNull(in);return new String(in.readAllBytes(),StandardCharsets.UTF_8);}
 }
}
