#Requires -Version 7.3
$ErrorActionPreference='Stop'
$demoTestRoot=Join-Path ([System.IO.Path]::GetTempPath()) ('reset-check-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path "$demoTestRoot/scripts","$demoTestRoot/docs/demo" -Force > $null
Copy-Item "$(Split-Path -Parent $PSScriptRoot)/reset-local-demo.ps1" "$demoTestRoot/scripts/"
Set-Content "$demoTestRoot/docker-compose.yml" 'services: {}'
Set-Content "$demoTestRoot/docs/demo/setup-multi-vendor-demo.sql" 'CREATE PROCEDURE demo_fixture();'
$global:demoCalls=[Collections.Generic.List[string]]::new()
$global:demoCase='happy'
function global:docker {
    $line=$args -join ' '
    $global:demoCalls.Add($line)
    $global:LASTEXITCODE=0
    if($line.EndsWith('config --volumes')) { 'mysql-data';return }
    if($line.EndsWith('config --format json')) {
        $profile=if($global:demoCase -eq 'production'){'production'}else{'local'}
        @{services=@{backend=@{environment=@{SPRING_PROFILES_ACTIVE=$profile}}}}|ConvertTo-Json -Depth 6 -Compress
        return
    }
    if($line.Contains('SELECT COUNT(*)')) {'1';return}
    if($line.Contains('exec -T mysql')) {
        $seed=$input|Out-String
        if(-not $seed.Contains('CREATE PROCEDURE demo_fixture')){throw 'Seed was not streamed'}
        if($global:demoCase -eq 'seed-failure'){$global:LASTEXITCODE=1}
    }
}
function global:Invoke-RestMethod { @('saumya-rooftops','helio-rural','lumina-lighting')|ForEach-Object { @{slug=$_} } }
function Check-True($value,$message){if(-not $value){throw $message}}
$script=Join-Path $demoTestRoot 'scripts/reset-local-demo.ps1'
try{& $script;throw 'Missing env was accepted'}catch{Check-True ($_.Exception.Message -like 'Create .env*') 'Wrong missing-env error'}
Check-True ($global:demoCalls.Count -eq 0) 'Docker called without env'
Set-Content "$demoTestRoot/.env" 'DEMO_TEST_ONLY=true'
$global:demoCase='production'
try{& $script;throw 'Production accepted'}catch{Check-True ($_.Exception.Message -like '*requires explicit local/test*') 'Wrong profile error'}
Check-True (-not ($global:demoCalls|Where-Object {$_ -match ' down '})) 'Deletion attempted in production'
$global:demoCalls.Clear();$global:demoCase='happy'
& $script
Check-True (($global:demoCalls|Where-Object {$_ -match 'down --volumes --remove-orphans'}).Count -eq 1) 'Reset missing'
Check-True (($global:demoCalls|Where-Object {$_ -match 'stop backend'}).Count -eq 1) 'Backend not stopped for seed'
Check-True (($global:demoCalls|Where-Object {$_ -match 'start backend'}).Count -eq 1) 'Backend not restarted'
Check-True (($global:demoCalls|Where-Object {$_ -match 'up -d --build --no-deps frontend'}).Count -eq 1) 'Frontend not started'
$global:demoCalls.Clear();$global:demoCase='seed-failure'
try{& $script;throw 'Seed failure accepted'}catch{Check-True ($_.Exception.Message -like 'Demo seed failed*') 'Wrong seed error'}
Check-True (-not ($global:demoCalls|Where-Object {$_ -match 'start backend|--no-deps frontend'})) 'Startup continued after failed seed'
Write-Output 'PASS: missing env; production guard; successful reset/seed/start; seed failure stops startup. All Docker/HTTP calls were mocked.'
