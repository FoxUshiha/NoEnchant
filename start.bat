@echo off
title Compilador NoEnchant v1.0 - Java 17

echo ============================================
echo Compilador do Plugin NoEnchant
echo (Sistema de Gerenciamento de Encantamentos)
echo ============================================
echo.

echo Procurando Java 17 instalado...
echo.

set JDK_PATH=

rem Procura JDK 17 em locais comuns
for /d %%i in ("C:\Program Files\Java\jdk-17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\Java\jdk17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\AdoptOpenJDK\jdk-17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\OpenJDK\jdk-17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\Amazon Corretto\jdk17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\Microsoft\jdk-17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\BellSoft\LibericaJDK-17*") do set JDK_PATH=%%i
for /d %%i in ("C:\Program Files\Java\jdk-17*") do set JDK_PATH=%%i

if "%JDK_PATH%"=="" (
    echo ============================================
    echo ERRO: JDK 17 nao encontrado!
    echo Instale o Java 17 JDK e tente novamente.
    echo ============================================
    pause
    exit /b 1
)

echo Java 17 encontrado em: %JDK_PATH%
echo.

set JAVAC="%JDK_PATH%\bin\javac.exe"
set JAR="%JDK_PATH%\bin\jar.exe"

echo ============================================
echo Preparando ambiente de compilacao...
echo ============================================
echo.

echo Limpando pasta out...
if exist out (
    rmdir /s /q out >nul 2>&1
)
mkdir out
mkdir out\com
mkdir out\com\foxsrv
mkdir out\com\foxsrv\noenchant

echo.
echo ============================================
echo Verificando dependencias...
echo ============================================
echo.

REM Verificar Spigot API
if not exist spigot-api-1.20.1-R0.1-SNAPSHOT.jar (
    echo [ERRO] spigot-api-1.20.1-R0.1-SNAPSHOT.jar nao encontrado!
    echo.
    echo Certifique-se de que o arquivo spigot-api-1.20.1-R0.1-SNAPSHOT.jar esta na pasta raiz.
    dir *.jar 2>nul
    echo.
    pause
    exit /b 1
) else (
    echo [OK] Spigot API encontrado: spigot-api-1.20.1-R0.1-SNAPSHOT.jar
    set SPIGOT_PATH=spigot-api-1.20.1-R0.1-SNAPSHOT.jar
)

REM Verificar outras dependências (opcionais)
if exist Vault.jar (
    echo [OK] Vault API encontrado (opcional)
    set VAULT_PATH=Vault.jar
) else (
    echo [AVISO] Vault.jar nao encontrado (opcional - nao necessario para NoEnchant)
    set VAULT_PATH=
)

if exist CoinCard.jar (
    echo [OK] CoinCard API encontrado (opcional)
    set COINCARD_PATH=CoinCard.jar
) else (
    echo [AVISO] CoinCard.jar nao encontrado (opcional - nao necessario para NoEnchant)
    set COINCARD_PATH=
)

echo.
echo ============================================
echo Compilando NoEnchant...
echo ============================================
echo.

REM Montar classpath
set CLASSPATH="%SPIGOT_PATH%"
if defined COINCARD_PATH (
    set CLASSPATH=%CLASSPATH%;CoinCard.jar
)
if defined VAULT_PATH (
    set CLASSPATH=%CLASSPATH%;Vault.jar
)

REM Mostrar classpath para debug
echo Classpath: %CLASSPATH%
echo.

REM Verificar se o arquivo fonte existe
if not exist src\com\foxsrv\noenchant\NoEnchant.java (
    echo ============================================
    echo ERRO: Arquivo fonte nao encontrado!
    echo ============================================
    echo.
    echo Caminho esperado: src\com\foxsrv\noenchant\NoEnchant.java
    echo.
    echo Estrutura de diretorios atual:
    echo.
    if exist src (
        echo Conteudo de src:
        dir /s /b src
    ) else (
        echo Pasta src nao encontrada!
    )
    echo.
    pause
    exit /b 1
)

REM Compilar com as dependências necessárias
echo Compilando NoEnchant.java...
%JAVAC% --release 17 -d out ^
-classpath %CLASSPATH% ^
-sourcepath src ^
src\com\foxsrv\noenchant\NoEnchant.java

if %errorlevel% neq 0 (
    echo ============================================
    echo ERRO AO COMPILAR O PLUGIN!
    echo ============================================
    echo.
    echo Verifique os erros acima e corrija o codigo.
    echo.
    echo Possiveis causas:
    echo 1 - Erro de sintaxe no codigo
    echo 2 - Dependencias faltando
    echo 3 - Versao do Java incorreta
    pause
    exit /b 1
)

echo.
echo Compilacao concluida com sucesso!
echo.

echo ============================================
echo Copiando arquivos de recursos...
echo ============================================
echo.

REM Copiar plugin.yml
if exist resources\plugin.yml (
    copy resources\plugin.yml out\ >nul
    echo [OK] plugin.yml copiado de resources\
) else (
    echo [AVISO] plugin.yml nao encontrado em resources\
    echo Criando plugin.yml padrao...
    
    (
        echo name: NoEnchant
        echo version: 1.0.0
        echo main: com.foxsrv.noenchant.NoEnchant
        echo api-version: 1.20
        echo author: FoxOficial2
        echo description: Remove non-whitelisted enchantments from items
        echo.
        echo commands:
        echo   noenchant:
        echo     description: Main NoEnchant command
        echo     usage: /noenchant ^<reload^|enchantname^> ^<true/false^>
        echo     aliases: [ne]
        echo     permission: noenchant.use
        echo.
        echo permissions:
        echo   noenchant.use:
        echo     description: Allows using the NoEnchant command
        echo     default: true
        echo   noenchant.reload:
        echo     description: Allows reloading the configuration
        echo     default: op
        echo   noenchant.modify:
        echo     description: Allows modifying the whitelist
        echo     default: op
    ) > out\plugin.yml
    echo [OK] plugin.yml criado automaticamente
)

REM Copiar config.yml
if exist resources\config.yml (
    copy resources\config.yml out\ >nul
    echo [OK] config.yml copiado de resources\
) else (
    echo [AVISO] config.yml nao encontrado em resources\
    echo Criando config.yml padrao...
    
    (
        echo # NoEnchant Configuration
        echo # List of enchantments that are allowed to remain on items
        echo # Enchantment names should be in UPPERCASE
        echo Whitelist:
        echo   - "PROTECTION"
        echo   - "SILK_TOUCH"
        echo   - "UNBREAKING"
        echo   - "MENDING"
    ) > out\config.yml
    echo [OK] config.yml criado automaticamente
)

echo.
echo ============================================
echo Criando arquivo JAR...
echo ============================================
echo.

cd out

REM Criar JAR com todos os recursos
echo Criando NoEnchant.jar...
%JAR% cf NoEnchant.jar com plugin.yml config.yml

cd ..

echo.
echo ============================================
echo PLUGIN COMPILADO COM SUCESSO!
echo ============================================
echo.
echo Arquivo gerado: out\NoEnchant.jar
echo.
dir out\NoEnchant.jar
echo.
echo ============================================
echo RESUMO DA COMPILACAO:
echo ============================================
echo.
echo - Data/Hora: %date% %time%
echo - Java Version: 17
echo - JDK Path: %JDK_PATH%
echo - Spigot API: OK (spigot-api-1.20.1-R0.1-SNAPSHOT.jar)
if defined COINCARD_PATH (
    echo - CoinCard API: OK (opcional)
) else (
    echo - CoinCard API: NAO ENCONTRADO (opcional)
)
if defined VAULT_PATH (
    echo - Vault API: OK (opcional)
) else (
    echo - Vault API: NAO ENCONTRADO (opcional)
)
echo - Arquivo fonte: src\com\foxsrv\noenchant\NoEnchant.java
echo.
echo ============================================
echo INFORMACOES DO PLUGIN:
echo ============================================
echo.
echo Nome: NoEnchant
echo Versao: 1.0.0
echo Pacote: com.foxsrv.noenchant
echo.
echo ============================================
echo COMANDOS DISPONIVEIS:
echo ============================================
echo.
echo /ne ou /noenchant - Comando principal
echo /ne reload - Recarrega configuracao (OP)
echo /ne ENCHANTMENT true/false - Adiciona/remove da whitelist
echo.
echo Exemplo: /ne FIRE_PROTECTION true
echo.
echo ============================================
echo FUNCIONALIDADES:
echo ============================================
echo.
echo - Remove automaticamente encantamentos nao permitidos
echo - Whitelist configuravel em config.yml
echo - Comando para modificar whitelist em jogo
echo - Tab complete com todos os encantamentos
echo - Logs detalhados no console
echo - Suporte a Paper (processamento assincrono)
echo - Verificacao periodica do inventario
echo.
echo ============================================
echo PARA INSTALAR:
echo ============================================
echo.
echo 1 - Copie out\NoEnchant.jar para a pasta plugins do servidor
echo 2 - Reinicie o servidor ou use /reload confirm
echo 3 - Edite plugins/NoEnchant/config.yml se necessario
echo.
echo ============================================
echo.

pause