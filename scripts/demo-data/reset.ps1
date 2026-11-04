param(
    [string]$Database = "clinicmanager",
    [string]$DbHost = "localhost",
    [int]$Port = 5432,
    [string]$User = "postgres",
    [string]$CardFiles = "",
    [string]$Psql = ""
)

$ErrorActionPreference = "Stop"

if ($Database -ne "clinicmanager" -and $Database -notlike "*demo*") {
    Write-Error "Refusing to drop '$Database'. Only 'clinicmanager' or a database with 'demo' in its name can be reset."
}
if ($DbHost -ne "localhost" -and $DbHost -ne "127.0.0.1") {
    Write-Error "Refusing to reset a database on '$DbHost'. Only localhost is allowed."
}

if (-not $Psql) {
    $found = Get-Command psql -ErrorAction SilentlyContinue
    if ($found) {
        $Psql = $found.Source
    } else {
        $Psql = Get-ChildItem "C:\Program Files\PostgreSQL\*\bin\psql.exe" -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
    }
}
if (-not $Psql) {
    Write-Error "psql not found. Pass -Psql 'C:\Program Files\PostgreSQL\16\bin\psql.exe'."
}

if (-not $env:PGPASSWORD) {
    $secure = Read-Host "Password for PostgreSQL user '$User'" -AsSecureString
    $env:PGPASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
}

$answer = Read-Host "This deletes ALL data in '$Database' on ${DbHost}:$Port. Type RESET to continue"
if ($answer -ne "RESET") {
    Write-Host "Cancelled."
    exit 0
}

& $Psql -h $DbHost -p $Port -U $User -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS $Database WITH (FORCE);"
& $Psql -h $DbHost -p $Port -U $User -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE $Database;"

if ($CardFiles) {
    if (Test-Path $CardFiles) {
        Get-ChildItem $CardFiles -Force | Remove-Item -Recurse -Force -Confirm:$false
        Write-Host "Cleared card files in $CardFiles"
    }
}

Write-Host "Database '$Database' recreated. Start the backend again - Liquibase rebuilds the schema and the first admin is created from ADMIN_INITIAL_USERNAME / ADMIN_INITIAL_PASSWORD."
