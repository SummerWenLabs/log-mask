param(
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$SpringBootVersion = '2.7.18'
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

function Assert-NoSpringDependencies([string]$module) {
    $dependencies = Get-ProjectDependencies (Join-Path $RepositoryRoot "$module/pom.xml")
    $springDependencies = @($dependencies | Where-Object {
        $_ -match '^org\.springframework(?:\.|:)'
    })
    if ($springDependencies.Count -gt 0) {
        throw "$module must remain Spring-free: $($springDependencies -join ', ')"
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

function Assert-ApacheLicenseHeaders {
    $header = 'SPDX-License-Identifier: Apache-2.0'
    $javaFiles = @(git -C $RepositoryRoot ls-files --cached --others `
        --exclude-standard -- '*.java')
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to enumerate Java sources.'
    }
    foreach ($relativePath in $javaFiles) {
        $path = Join-Path $RepositoryRoot $relativePath
        $firstLine = Get-Content -LiteralPath $path -TotalCount 1
        if ($firstLine -ne "/* $header */") {
            throw "$relativePath is missing the Apache-2.0 SPDX header."
        }
    }
}

function Assert-DependencyLicenses {
    $pomPath = Join-Path $RepositoryRoot 'pom.xml'
    & mvn -B -ntp -f $pomPath `
        org.codehaus.mojo:license-maven-plugin:2.4.0:aggregate-add-third-party `
        '-Dlicense.excludedScopes=test' `
        '-Dlicense.failOnMissing=true' `
        '-Dlicense.force=true' `
        '-Dlicense.sortArtifactByName=true' `
        "-Dspring-boot.version=$SpringBootVersion"
    if ($LASTEXITCODE -ne 0) {
        throw 'Dependency license report generation failed.'
    }
    $reportPath = Join-Path $RepositoryRoot `
        'target/generated-sources/license/THIRD-PARTY.txt'
    if (-not (Test-Path -LiteralPath $reportPath)) {
        throw 'Dependency license report was not generated.'
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
Assert-NoSpringDependencies 'log-mask-core'
Assert-NoSpringDependencies 'log-mask-http-core'
Assert-NoSpringImports 'log-mask-core'
Assert-NoSpringImports 'log-mask-http-core'
Assert-Java8Bytecode
Assert-ApacheLicenseHeaders
Assert-DependencyLicenses

Write-Host 'Quality gates passed: dependencies, licenses, Spring-free cores, and Java 8 bytecode.'
