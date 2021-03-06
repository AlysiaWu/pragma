package cli

import pragma.domain._
import pragma.daemonProtocol._
import cats.implicits._
import scala.util._, scala.io.StdIn.readLine
import cli.utils._
import os.Path
import requests.RequestFailedException
import pragma.domain.utils.UserError

object Main {

  def main(args: Array[String]): Unit = {
    val config = CLIConfig.parse(args.toList)

    config.command match {
      case CLICommand.Root | CLICommand.Help => {
        print(CLIConfig.usageWithAsciiLogo)
        sys.exit(0)
      }
      case Dev => {
        tryOrExit(DaemonClient.ping.void)
        println(renderLogo)
        run(config, withReload = true)
      }
      case CLICommand.New => initProject()
      case Prod => {
        println("Production mode is not ready yet.")
        sys.exit(1)
      }
    }
  }

  def run(config: CLIConfig, withReload: Boolean = false): Try[Unit] = {
    lazy val code = tryOrExit(
      Try(os.read(config.filePath)),
      Some(s"Could not read ${config.filePath.toString}")
    )

    lazy val syntaxTree = SyntaxTree.from(code)

    lazy val devMigration = for {
      st <- syntaxTree
      projNameEntry = st.config.entryMap("projectName")
      projectName = projNameEntry.value
        .asInstanceOf[PStringValue]
        .value
      _ <- if (projectName contains "-")
        Failure {
          UserError(
            (
              "The project's name must not contain any dashes ('-')",
              projNameEntry.position
            ) :: Nil
          )
        } else Success(())
      functions <- st.functions.toList.traverse {
        case ExternalFunction(id, scopeName, filePathStr, runtime) => {
          val filePath = os.FilePath(filePathStr)
          for {
            (content, isBinary) <- {
              filePath match {
                case path: Path => readContent(path)
                case _ =>
                  readContent(config.projectPath / os.RelPath(filePathStr))
              }
            }
          } yield
            ImportedFunctionInput(id, scopeName, content, runtime, isBinary)
        }
        case otherFn =>
          Failure {
            new Exception {
              s"Unsupported function type `${otherFn.getClass.getCanonicalName}`"
            }
          }
      }
      _ <- DaemonClient
        .createProject(ProjectInput(projectName))
        .handleErrorWith {
          // Meaning project already exists
          case err: RequestFailedException if err.response.statusCode == 400 =>
            Success(())
          case err =>
            Failure(
              new Exception(s"Unable to create project\n${err.getMessage}")
            )
        }
        .void
      usedRuntimes <- usedFuntionRuntimes(st.imports.toList).toTry
      _ <- pullDockerRuntimeImages(usedRuntimes)
      migration = MigrationInput(code, functions.toList)
      _ <- DaemonClient.devMigrate(migration, projectName)
    } yield projectName

    config.command match {
      case Dev =>
        devMigration match {
          case Success(projectName) => println(welcomeMsq(projectName, Dev))
          case Failure(err)         => println(renderThrowable(err, code = Some(code)))
        }
      case _ => ()
    }

    if (withReload) reloadPrompt(config)
    else sys.exit(0)
  }

  def pullDockerRuntimeImages(runtimes: Set[RuntimeTag]): Try[Unit] = Try {
    import cli.utils.RuntimeTag._
    runtimes.toList.traverse { runtime =>
      val imageName = runtime match {
        case NodeJS10 => "openwhisk/action-nodejs-v10:nightly"
        case NodeJS14 => "openwhisk/action-nodejs-v14:nightly"
        case Python3  => "openwhisk/python3action:nightly"
      }

      Try {
        println(s"Pulling image $imageName...")
        os.proc("docker", "pull", imageName).call(cwd = os.pwd)
      } handleErrorWith { err =>
        Failure {
          new Exception(s"Failed to pull image $imageName\n${err.getMessage}")
        }
      }
    }
  }

  def reloadPrompt(config: CLIConfig): Try[Unit] =
    readLine("(r)eload, (q)uit: ") match {
      case "r" | "R" => {
        println("Reloading...")
        run(config, withReload = true)
      }
      case "q" | "Q" => {
        println("Come back soon!")
        sys.exit(0)
      }
      case unknown => {
        println(s"I do not know what `$unknown` means...")
        reloadPrompt(config)
      }
    }

  def initProject(): Try[Unit] = Try {
    val newProjectName = readLine("What's the name of your new project?:").trim
    if (newProjectName.isEmpty) {
      println("A project's name cannot be an empty string... Please try again")
      initProject()
    }
    val projectDir = os.pwd / newProjectName
    Try {
      os.makeDir(projectDir)
      val pragmafile =
        s"""
        |config { projectName = "$newProjectName" }
        |""".stripMargin

      os.write(projectDir / "Pragmafile", pragmafile)
    } *> Success(println("Project files successfully generated."))
  }

  val renderLogo =
    assets.asciiLogo
      .split("\n")
      .map(line => (" " * 24) + line)
      .mkString("\n")

}
