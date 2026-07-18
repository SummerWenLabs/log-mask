param(
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$SpringBoot2Version = '2.7.18',
    [string]$SpringBoot3Version = '3.5.16'
)

$ErrorActionPreference = 'Stop'

function Get-ReactorModules {
    [xml]$pom = Get-Content -Raw -LiteralPath (Join-Path $RepositoryRoot 'pom.xml')
    $namespace = New-Object System.Xml.XmlNamespaceManager($pom.NameTable)
    $namespace.AddNamespace('m', 'http://maven.apache.org/POM/4.0.0')
    return @($pom.SelectNodes('/m:project/m:modules/m:module', $namespace) |
        ForEach-Object { $_.InnerText })
}

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
    $pomPath = Join-Path $RepositoryRoot "$module/pom.xml"
    if (-not (Test-Path -LiteralPath $pomPath)) {
        throw "$module is listed in the module boundary gate but its pom.xml is missing."
    }
    $dependencies = Get-ProjectDependencies $pomPath
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

function Assert-ModuleBytecode(
    [string]$module,
    [int]$expectedMajorVersion,
    [string]$javaVersion
) {
    $moduleRoot = Join-Path $RepositoryRoot $module
    if (-not (Test-Path -LiteralPath $moduleRoot)) {
        throw "$module is listed in the bytecode gate but its directory is missing."
    }
    $classFiles = @(
        foreach ($outputDirectory in @('classes', 'test-classes')) {
            $outputRoot = Join-Path $moduleRoot "target/$outputDirectory"
            if (Test-Path -LiteralPath $outputRoot) {
                Get-ChildItem -LiteralPath $outputRoot -Recurse -Filter '*.class'
            }
        }
    )
    foreach ($classFile in $classFiles) {
        [byte[]]$header = [System.IO.File]::ReadAllBytes($classFile.FullName)
        if ($header.Length -lt 8 -or $header[0] -ne 0xCA -or $header[1] -ne 0xFE -or
                $header[2] -ne 0xBA -or $header[3] -ne 0xBE) {
            throw "$($classFile.FullName) is not a valid class file."
        }
        $major = ([int]$header[6] -shl 8) -bor [int]$header[7]
        if ($major -ne $expectedMajorVersion) {
            throw "$($classFile.FullName) has class-file major version $major; " +
                "$module requires Java $javaVersion class-file major version $expectedMajorVersion."
        }
    }
    return $classFiles.Count
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
        "-Dspring-boot2.version=$SpringBoot2Version" `
        "-Dspring-boot3.version=$SpringBoot3Version"
    if ($LASTEXITCODE -ne 0) {
        throw 'Dependency license report generation failed.'
    }
    $reportPath = Join-Path $RepositoryRoot `
        'target/generated-sources/license/THIRD-PARTY.txt'
    if (-not (Test-Path -LiteralPath $reportPath)) {
        throw 'Dependency license report was not generated.'
    }
}

$allowedProjectDependencies = @{
    'log-mask-core' = @()
    'log-mask-http-core' = @('io.github.summerwenlabs:log-mask-core')
    'log-mask-resttemplate-spring-boot2-autoconfigure' = @(
        'io.github.summerwenlabs:log-mask-http-core'
    )
    'log-mask-resttemplate-spring-boot2-starter' = @(
        'io.github.summerwenlabs:log-mask-resttemplate-spring-boot2-autoconfigure'
    )
    'log-mask-samples' = @(
        'io.github.summerwenlabs:log-mask-resttemplate-spring-boot2-starter'
    )
    'log-mask-resttemplate-spring-boot3-autoconfigure' = @(
        'io.github.summerwenlabs:log-mask-http-core'
    )
    'log-mask-resttemplate-spring-boot3-starter' = @(
        'io.github.summerwenlabs:log-mask-resttemplate-spring-boot3-autoconfigure'
    )
    'log-mask-samples-spring-boot3' = @(
        'io.github.summerwenlabs:log-mask-resttemplate-spring-boot3-starter'
    )
    'log-mask-benchmarks' = @(
        'io.github.summerwenlabs:log-mask-resttemplate-spring-boot2-starter'
    )
}

$modules = @(Get-ReactorModules)
if (Test-Path -LiteralPath (Join-Path $RepositoryRoot 'log-mask-benchmarks/pom.xml')) {
    $modules += 'log-mask-benchmarks'
}
$unmappedModules = @($modules | Where-Object {
    -not $allowedProjectDependencies.ContainsKey($_)
})
if ($unmappedModules.Count -gt 0) {
    throw "Modules are missing dependency boundary definitions: $($unmappedModules -join ', ')"
}
foreach ($module in $modules) {
    Assert-ProjectDependencies $module $allowedProjectDependencies[$module]
}
Assert-NoSpringDependencies 'log-mask-core'
Assert-NoSpringDependencies 'log-mask-http-core'
Assert-NoSpringImports 'log-mask-core'
Assert-NoSpringImports 'log-mask-http-core'
$compiledClassCount = 0
foreach ($module in $modules) {
    if ($module -like '*spring-boot3*') {
        $compiledClassCount += Assert-ModuleBytecode $module 61 '17'
    } else {
        $compiledClassCount += Assert-ModuleBytecode $module 52 '8'
    }
}
if ($compiledClassCount -eq 0) {
    throw 'No compiled project classes were found; run mvn clean verify before this gate.'
}
Assert-ApacheLicenseHeaders
Assert-DependencyLicenses

Write-Host 'Quality gates passed: dependencies, licenses, Spring-free cores, and Java 8/17 bytecode.'
