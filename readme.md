# 🐱 Angora Cat Monitor - Kotlin Version

Monitor automatizado para anúncios de gatos Angorá no OLX com notificações via Discord.

## 📋 Sobre o Projeto

Este projeto foi originalmente desenvolvido em Python e convertido para Kotlin como exercício de aprendizado. Ele monitora automaticamente anúncios de gatos Angorá na região de Piracicaba no OLX e envia notificações via webhook do Discord quando encontra anúncios abaixo de R$ 300.

## 🚀 Tecnologias Utilizadas

- **Kotlin** - Linguagem principal
- **Kotlinx Coroutines** - Programação assíncrona
- **Kotlinx Serialization** - Serialização JSON
- **OkHttp** - Cliente HTTP
- **Jsoup** - Parser HTML
- **Gradle** - Build tool


## 🛠️ Configuração

### Pré-requisitos

- JDK 17 ou superior
- Gradle (ou usar o wrapper incluído)

### Instalação no Arch Linux

```bash
# Instalar JDK
sudo pacman -S jdk17-openjdk

# Verificar instalação
java -version
```

### Configuração do Projeto

1. Clone ou crie o diretório do projeto
2. Configure o webhook do Discord no código (substitua a URL)
3. Execute o projeto

## 🏃‍♂️ Como Executar

### Usando Gradle

```bash
# Compilar o projeto
./gradlew build

# Executar o monitor
./gradlew run

# Ou usar o task customizado
./gradlew runMonitor
```

## 🔧 Funcionalidades

- ✅ Web scraping do OLX
- ✅ Filtro por palavra-chave "angorá"
- ✅ Filtro por preço (< R$ 300)
- ✅ Cache para evitar notificações duplicadas
- ✅ Notificações via Discord webhook
- ✅ Tratamento de erros
- ✅ Logs informativos

## 📝 Configuração Avançada

### Personalizando Filtros

No arquivo `AngoraMonitor.kt`, você pode modificar:

```kotlin
// Preço máximo
if ("angorá" in title.lowercase() && price < 300.0 && link !in alreadySent) {

// URL de busca
const val SEARCH_URL = "https://www.olx.com.br/animais-e-acessorios/gatos?q=angor%C3%A1&region=piracicaba"
```

### Configurando Webhook do Discord

1. No seu servidor Discord, vá em Configurações do Canal
2. Integrações → Webhooks → Novo Webhook
3. Copie a URL e substitua na constante `WEBHOOK_URL`

## 🤝 Diferenças da Versão Python

### Melhorias Implementadas

- **Programação Assíncrona**: Uso de corrotinas para melhor performance
- **Type Safety**: Sistema de tipos mais robusto do Kotlin
- **Tratamento de Erros**: Melhor handling de exceções
- **Logs Melhorados**: Emojis e mensagens mais informativas
- **Arquitetura**: Código organizado em classe com responsabilidades bem definidas

### Semelhanças Mantidas

- Mesma lógica de cache
- Mesmo sistema de filtros
- Mesma integração com Discord
- Mesma funcionalidade de parsing de preços

## 🔄 Automação (Opcional)

Para executar automaticamente, crie um cron job:

```bash
# Editar crontab
crontab -e

# Adicionar linha para executar a cada 30 minutos
*/30 * * * * cd /caminho/para/projeto && ./gradlew run >/dev/null 2>&1
```

## 📊 Exemplo de Saída

```
🔍 Verificando novos anúncios...
📋 Encontrados 15 anúncios
🎯 Novo anúncio encontrado: Gato Angorá filhote - R$250.00
✅ Notificação enviada para Discord!
✨ 1 novo(s) anúncio(s) enviado(s)!
```

## 📄 Licença

Este projeto é de código aberto e pode ser usado livremente para fins educacionais.

## 🤖 Contribuições

Sinta-se livre para contribuir com melhorias, correções de bugs ou novas funcionalidades!

---

*Projeto desenvolvido como exercício de conversão Python → Kotlin*