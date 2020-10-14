package cli

import pragma.domain._
import pragma.daemonProtocol._
import cats.implicits._
import scala.util._, scala.io.StdIn.readLine
import cli.utils._

object Main {

  def main(args: Array[String]): Unit = {
    val config = tryOrExit(CLIConfig.parse(args.toList))
    config.command match {
      case CLICommand.Dev => {
        println(renderLogo)
        run(config, withReload = true)
      }
      case CLICommand.Prod   => tryOrExit(run(config))
      case CLICommand.Create => createNewProject()
      case CLICommand.Root   => ()
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
      projectName = st.config
        .entryMap("projectName")
        .value
        .asInstanceOf[PStringValue]
      functions <- st.functions.toList.traverse {
        case ExternalFunction(id, filePath, runtime) =>
          for {
            (content, isBinary) <- readContent(os.pwd / os.RelPath(filePath))
          } yield ImportedFunctionInput(id, content, runtime, isBinary)
        case otherFn =>
          Failure {
            new Exception {
              s"Unsupported function type `${otherFn.getClass.getCanonicalName}`"
            }
          }
      }
      migration = MigrationInput(code, functions.toList)
      _ <- DaemonClient.devMigrate(migration, projectName.value)
    } yield ()

    config.command match {
      case CLICommand.Dev =>
        devMigration match {
          case Success(_)   => ()
          case Failure(err) => println(renderThrowable(err, code = Some(code)))
        }
      case CLICommand.Prod => {
        println("Sorry, production mode is not yet implemented.")
        sys.exit(0)
      }
      case CLICommand.Create => createNewProject()
      case _                 => ()
    }

    if (withReload) reloadPrompt(config)
    else sys.exit(0)
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

  def createNewProject(): Try[Unit] = Try {
    val newProjectName = readLine("What's the name of your new project?:").trim
    if (newProjectName.isEmpty) {
      println("A project's name cannot be an empty string...")
      sys.exit(1)
    }
    val projectDir = os.pwd / newProjectName
    val createProj =
      DaemonClient.createProject(
        ProjectInput(
          newProjectName,
          "DUMMY_SECRET",
          "jdbc:postgresql://localhost:5433/test",
          "test",
          "test"
        )
      ) *> Try {
        os.makeDir(projectDir)
        val pragmafile =
          s"""
        |config { projectName = "$newProjectName" }
        |""".stripMargin
        os.write(projectDir / "Pragmafile", pragmafile)
      }

    createProj.handleErrorWith {
      case err => {
        println(renderThrowable(err))
        sys.exit(1)
      }
    }
  }

  val renderLogo =
    assets.asciiLogo
      .split("\n")
      .map(line => (" " * 24) + line)
      .mkString("\n")

  val welcomeMsq = s"""
        Pragma GraphQL server is now running on port 3030

                  ${Console.GREEN}${Console.BOLD}http://localhost:3030/graphql${Console.RESET}

          Enter 'q' to quit, or anything else to reload
      """

}
