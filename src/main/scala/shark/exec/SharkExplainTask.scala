package shark.exec

import java.io.PrintStream
import java.lang.reflect.Method
import java.util.{Arrays, HashSet, List => JavaList}

import org.apache.hadoop.fs.Path
import org.apache.hadoop.hive.conf.HiveConf
import org.apache.hadoop.hive.ql.exec.{ExplainTask, Task}
import org.apache.hadoop.hive.ql.{Context, DriverContext, QueryPlan}
import org.apache.hadoop.hive.ql.plan.{Explain, ExplainWork}
import org.apache.hadoop.hive.ql.plan.api.StageType
import org.apache.hadoop.util.StringUtils

import scala.collection.JavaConversions._

import shark.LogHelper


class SharkExplainWork(
  resFile: String,
  rootTasks: JavaList[Task[_ <: java.io.Serializable]],
  astStringTree: String,
  extended: Boolean)
 extends ExplainWork(resFile, rootTasks, astStringTree, extended)


/**
 * SharkExplainTask executes EXPLAIN for RDD operators.
 */
class SharkExplainTask extends Task[SharkExplainWork] with java.io.Serializable with LogHelper {

  val hiveExplainTask = new ExplainTask

  override def execute(driverContext: DriverContext): Int = {
    logInfo("Executing " + this.getClass.getName())
    hiveExplainTask.setWork(work)
    
    try {
      val resFile = new Path(work.getResFile())
      val outS = resFile.getFileSystem(conf).create(resFile)
      val out = new PrintStream(outS)
      
      // Print out the parse AST
      hiveExplainTask.outputAST(work.getAstStringTree, out, 0)
      out.println()

      hiveExplainTask.outputDependencies(out, work.getRootTasks, 0)
      out.println()

      // Go over all the tasks and dump out the plans
      hiveExplainTask.outputStagePlans(out, work.getRootTasks, 0)
      
      // Print the Shark query plan if applicable.
      if (work != null && work.getRootTasks != null && work.getRootTasks.size > 0) {
        work.getRootTasks.zipWithIndex.foreach { case(task, taskIndex) =>
          task match {
            case sparkTask: SparkTask => {
              out.println("SHARK QUERY PLAN #%d:".format(taskIndex))
              val terminalOp = sparkTask.getWork().terminalOperator
              ExplainTaskHelper.outputPlan(terminalOp, out, work.getExtended, 2)
              out.println()
            }
            case _ => null
          }
        }
      }

      out.close()
      0
    } catch {
      case e: Exception => {
        console.printError("Failed with exception " + e.getMessage(), "\n" +
            StringUtils.stringifyException(e))
        throw(e)
        1
      }
    }
  }
  
  override def initialize(conf: HiveConf, queryPlan: QueryPlan, driverContext: DriverContext) {
    hiveExplainTask.initialize(conf, queryPlan, driverContext)
    super.initialize(conf, queryPlan, driverContext)
  }

  override def getType = hiveExplainTask.getType

  override def getName = hiveExplainTask.getName

  override def localizeMRTmpFilesImpl(ctx: Context) {
    // explain task has nothing to localize
    // we don't expect to enter this code path at all
    throw new RuntimeException ("Unexpected call")
  }

}

