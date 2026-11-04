package io.github.saonte.mangareader.rulelint

import io.github.saonte.mangareader.source.rule.IssueSeverity
import io.github.saonte.mangareader.source.rule.RuleCompileResult
import io.github.saonte.mangareader.source.rule.RuleCompiler
import java.io.File
import kotlin.system.exitProcess

/**
 * 规则校验 CLI（供规则仓库 CI 与作者本地使用）：
 *
 *     ./gradlew :tools:rulelint:run --args="rule.json another.json"
 *
 * 退出码：0 = 全部通过（允许 warning）；1 = 存在 error；2 = 参数错误。
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("用法：rulelint <rule.json> [more.json ...]")
        exitProcess(2)
    }

    val compiler = RuleCompiler()
    var errorFiles = 0
    var warningCount = 0

    args.forEach { path ->
        val file = File(path)
        if (!file.canRead()) {
            System.err.println("FAIL $path：文件不可读")
            errorFiles += 1
            return@forEach
        }
        when (val result = compiler.compile(file.readText())) {
            is RuleCompileResult.Success -> {
                println("OK   $path  (${result.rule.id} v${result.rule.version})")
                result.issues.forEach { issue ->
                    warningCount += 1
                    println("  WARN ${issue.pointer}: ${issue.message}${issue.hint?.let { " ($it)" }.orEmpty()}")
                }
            }

            is RuleCompileResult.Failure -> {
                errorFiles += 1
                println("FAIL $path")
                result.issues.forEach { issue ->
                    val level = if (issue.severity == IssueSeverity.ERROR) "ERROR" else "WARN"
                    println("  $level ${issue.pointer}: ${issue.message}${issue.hint?.let { " ($it)" }.orEmpty()}")
                }
            }
        }
    }

    println("检查完成：${args.size} 个文件，错误 $errorFiles 个，警告 $warningCount 条")
    exitProcess(if (errorFiles > 0) 1 else 0)
}
