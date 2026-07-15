param(
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'

function Get-ProjectDependencies([string]$pomPath) {
    [xml]$pom = Get-Content -Raw -LiteralPath $pomPath
    $namespace = New-Object System.Xml.XmlNamespaceManager($pom.NameTable)
    $namespace.AddNamespace('m', 'http://maven.apache.org/POM/4.0.0')
    return @($pom.SelectNodes('/m:project/m:dependencies/m:dependency', $namespace) | ForEach-Object {
        "$($_.groupId):$($_.artifactId)"
    })
}

function Assert-ProjectDependencies(
    [string]$module,
    [string[]]$allowedProjectDependencies
) {
    $dependencies = Get-ProjectDependencies (Join-Path $RepositoryRoot "$module/pom.xml")
    $projectDependencies = @($dependencies | Where-Object { $_ -like 'io.github.summerwenlabs:log-mask-*' })
    $unexpected = @($projectDependencies | Where-Object { $allowedProjectDependencies -notcontains $_ })
    if ($unexpected.Count -gt 0) {
        throw "$module violates the module dependency boundary: $($unexpected -join ', ')"
    }
}

function Assert-NoSpringImports([string]$module) {
    $sourceRoot = Join-Path $RepositoryRoot "$module/src/main/java"
    if (Test-Path -LiteralPath $sourceRoot) {
        $matches = @(Get-ChildItem -LiteralPath $sourceRoot -Recurse -Filter '*.java' |
            Select-String -Pattern '^import org\.springframework\.' -ErrorAction Stop)
        if ($matches.Count -gt 0) {
            throw "$module must remain Spring-free: $($matches[0].Path):$($matches[0].LineNumber)"
        }
    }
}

function Assert-Java8Bytecode {
    $classFiles = @(Get-ChildItem -LiteralPath $RepositoryRoot -Recurse -Filter '*.class' |
        Where-Object { $_.FullName -match '[\\/]target[\\/](classes|test-classes)[\\/]' })
    if ($classFiles.Count -eq 0) {
        throw 'No compiled project classes were found; run mvn clean verify before this gate.'
    }
    foreach ($classFile in $classFiles) {
        [byte[]]$header = [System.IO.File]::ReadAllBytes($classFile.FullName)
        if ($header.Length -lt 8 -or $header[0] -ne 0xCA -or $header[1] -ne 0xFE -or
                $header[2] -ne 0xBA -or $header[3] -ne 0xBE) {
            throw "$($classFile.FullName) is not a valid class file."
        }
        $major = ([int]$header[6] -shl 8) -bor [int]$header[7]
        if ($major -gt 52) {
            throw "$($classFile.FullName) has class-file major version $major; Java 8 requires at most 52."
        }
    }
}

Assert-ProjectDependencies 'log-mask-core' @()
Assert-ProjectDependencies 'log-mask-http-core' @('io.github.summerwenlabs:log-mask-core')
Assert-ProjectDependencies 'log-mask-resttemplate-spring-boot2-autoconfigure' @(
    'io.github.summerwenlabs:log-mask-http-core'
)
Assert-ProjectDependencies 'log-mask-resttemplate-spring-boot2-starter' @(
    'io.github.summerwenlabs:log-mask-resttemplate-spring-boot2-autoconfigure'
)
Assert-ProjectDependencies 'log-mask-samples' @(
    'io.github.summerwenlabs:log-mask-resttemplate-spring-boot2-starter'
)
Assert-ProjectDependencies 'log-mask-benchmarks' @(
    'io.github.summerwenlabs:log-mask-resttemplate-spring-boot2-starter'
)
Assert-NoSpringImports 'log-mask-core'
Assert-NoSpringImports 'log-mask-http-core'
Assert-Java8Bytecode

Write-Host 'Quality gates passed: module boundaries, Spring-free cores, and Java 8 bytecode.'
