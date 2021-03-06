package shark

import java.io.{File, FileOutputStream, PrintStream}

import org.apache.commons.lang.StringUtils
import org.apache.hadoop.hive.cli.CliSessionState
import org.apache.hadoop.hive.conf.HiveConf
import org.apache.hadoop.hive.ql.exec.Utilities.StreamPrinter
import org.apache.hadoop.hive.ql.session.SessionState
import org.apache.hadoop.hive.ql.QTestUtil

class SharkQTestUtil(outDir: String, logDir: String) extends QTestUtil(outDir, logDir) {

  val queryMap = getQMap

  var cliDrv: SharkCliDriver = null

  override def cliInit(tname:String, recreate:Boolean) {
    //HiveConf.setVar(conf, HiveConf.ConfVars.HIVE_AUTHENTICATOR_MANAGER,
    //"org.apache.hadoop.hive.ql.security.DummyAuthenticator")
    
    SharkConfVars.setVar(conf, SharkConfVars.EXPLAIN_MODE, "hive")
    
    val ss = new CliSessionState(conf)
    assert(ss != null)
    ss.in = System.in
    
    val qf = new File(outDir, tname)
    var outf = new File(logDir)
    outf = new File(outf, qf.getName.concat(".out"))
    val fo = new FileOutputStream(outf)
    ss.out = new PrintStream(fo, true, "UTF-8")
    ss.err = ss.out
    ss.setIsSilent(true)
    val oldSs = SessionState.get()
    if (oldSs != null && oldSs.out != null && oldSs.out != System.out) {
      oldSs.out.close()
    }
    SessionState.start(ss)
    
    cliDrv = new SharkCliDriver()
    //    if (tname.equals("init_file.q"))
    //      ss.initFiles.add("../data/scripts/test_init_file.sql")
    cliDrv.processInitFiles(ss)
  }

  override def executeClient(tname: String): Int = {
    cliDrv.processLine(queryMap.get(tname))
  }

  override def checkCliDriverResults(tname: String): Int = {
    val outFileName = outPath(outDir, tname + ".out")
    val cmdArray: Array[String] = Array(
      "diff", "-a",
      "-I", "file:",
      "-I", "pfile:",
      "-I", "hdfs:",
      "-I", "/tmp/",
      "-I", "invalidscheme:",
      "-I", "lastUpdateTime",
      "-I", "lastAccessTime",
      "-I", "[Oo]wner",
      "-I", "CreateTime",
      "-I", "LastAccessTime",
      "-I", "Location",
      "-I", "transient_lastDdlTime",
      "-I", "last_modified_",
      "-I", "java.lang.RuntimeException",
      "-I", "at org",
      "-I", "at sun",
      "-I", "at java",
      "-I", "at junit",
      "-I", "Caused by:",
      "-I", "LOCK_QUERYID:",
      "-I", "grantTime",
      "-I", "[.][.][.] [0-9]* more",
      "-I", "USING 'java -cp",
      "-I", "PREHOOK",
      "-I", "POSTHOOK")

    val cmdString = 
      "\"" +
      StringUtils.join(cmdArray.asInstanceOf[Array[Object]], "\" \"") + "\" " + 
      "<(sort " + (new File(logDir, tname + ".out")).getPath() + ") " + 
      "<(sort " + outFileName + ")"

    println(cmdString)
    val bashCmdArray = Array("bash", "-c", cmdString)
    //println(StringUtils.join(cmdArray.asInstanceOf[Array[Object]], ' '))
  
    val executor = Runtime.getRuntime().exec(bashCmdArray);

    val outPrinter = new StreamPrinter(
        executor.getInputStream(), null, SessionState.getConsole().getChildOutStream())
    val errPrinter = new StreamPrinter(
        executor.getErrorStream(), null, SessionState.getConsole().getChildErrStream())

    outPrinter.start();
    errPrinter.start();

    val exitVal = executor.waitFor();

    exitVal
  }

}
