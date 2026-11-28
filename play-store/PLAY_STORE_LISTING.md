# Kit de Publicação — Google Play Console (React Studio)

Copie e cole os textos abaixo diretamente nos campos do **Google Play Console**.

---

## 1. Detalhes do App (Ficha da Play Store)

- **ID do pacote:** `com.aistudio.reactstudio.vzqpmr`
- **Nome do app (máx. 30 caracteres):**
  `React Studio`
- **Categoria recomendada:** `Reprodutores e editores de vídeo` (Video Players & Editors)
- **E-mail de contato / suporte:** `lojadolar@gmail.com`
- **URL da Política de Privacidade** *(após ativar GitHub Pages na pasta `/docs` do branch `main`)*:
  `https://notavel2019.github.io/react/privacy.html`

---

## 2. Descrição Curta (máx. 80 caracteres — 77 caracteres usados)

```text
Grave reações em tela dividida com enquadramento livre, sincronia e áudio pro
```

---

## 3. Descrição Completa (máx. 4.000 caracteres)

```text
Crie vídeos de reação profissionais direto do seu celular com o React Studio.

Com tela dividida inteligente, o vídeo principal fica na parte superior e a sua câmera na parte inferior, prontos para gravar reações sincronizadas, combinar tudo em um único MP4 e compartilhar nas suas redes sociais.

RECURSOS PRINCIPAIS

• Tela dividida sincronizada (Vídeo em cima + Câmera embaixo)
Ao tocar em Gravar, o vídeo superior inicia automaticamente em sincronia com a câmera inferior. Depois de iniciar, você tem controle livre para pausar, retomar, adiantar, retroceder ou ajustar a velocidade do vídeo a qualquer momento.

• Enquadramento preciso sem distorção
Use gestos de pinça para zoom e arraste com o dedo para posicionar o vídeo exatamente como quiser no quadrante superior, mantendo a proporção real da imagem. Alterne entre Preencher, Ajustar e guias visuais 1:1, 9:16, 16:9 e 4:3, além da divisão de tela 50/50, 60/40 ou 40/60.

• Vídeos da galeria, links MP4 e Instagram Reels
Escolha vídeos direto da galeria usando o seletor nativo do Android ou cole um link de vídeo/Reels para enquadrar no palco superior.

• Mesa de Áudio para Microfone e Fone de Ouvido
Ajuste ganho de microfone, supressão de ruído, cancelamento de eco, nivelamento automático (AGC), retorno de voz seguro no fone de ouvido (com detecção automática de fone para evitar microfonia), balanço vídeo/voz e perfis de equalização.

• Reação Automática por Voz (Auto-React)
Detecta automaticamente quando você ou outra pessoa fala na câmera para abaixar suavemente o volume do vídeo (Auto-Duck), pausar e retomar após o silêncio ou registrar marcadores na linha do tempo.

• Exportação Combinada em MP4 e Compartilhamento
Salve o vídeo combinado em tela dividida na galeria (Movies/ReactStudio) e compartilhe facilmente no Instagram, TikTok, WhatsApp, YouTube ou Google Drive.

PRIVACIDADE
Todo o processamento de câmera, microfone e vídeo acontece localmente no seu dispositivo.

TESTE DE 4 DIAS E ASSINATURA
• Experimente todos os recursos do React Studio por 4 dias corridos.
• Após o período de teste, continue usando com a assinatura React Studio Pro Anual por R$ 20,00/ano, cobrada e gerenciada com segurança pela Google Play.
• A assinatura renova automaticamente a cada 12 meses e pode ser cancelada quando quiser nas configurações da Google Play.
```

---

## 4. Notas da Versão (O que há de novo nesta versão)

```text
• Lançamento oficial do React Studio
• Gravação em tela dividida com início sincronizado automático
• Enquadramento com pinça e arraste sem distorção
• Mesa de Áudio completa para microfone, fone de ouvido e Auto-React
• Exportação combinada em MP4 e compartilhamento direto
• Teste completo de 4 dias e plano anual via Google Play
```

---

## 5. Configuração da Assinatura no Play Console

Vá em **Monetizar com o Google Play → Produtos → Assinaturas → Criar assinatura**:

| Campo | Valor exato |
|---|---|
| **ID do produto** | `react_studio_annual` |
| **Nome** | `React Studio Pro Anual` |
| **ID do plano básico (Base plan ID)** | `annual-plan` |
| **Tipo** | Renovação automática |
| **Período de faturamento** | 1 ano |
| **Período de carência** | 7 dias (recomendado) |
| **Preço (Brasil)** | `R$ 20,00` |
| **ID da oferta (Offer ID)** | `trial-4d` *(opcional — teste grátis no checkout Play)* |
| **Tag da oferta** | `trial-4d` |
| **Elegibilidade** | Novos clientes (nunca assinaram) |
| **Fase 1 (Teste gratuito)** | `4 dias` (`R$ 0,00`) |
| **Status** | **Ativar** o plano básico e a oferta |

---

## 6. Respostas dos Formulários Obrigatórios do Play Console

### A) Segurança dos dados (Data Safety)
- **O app coleta ou compartilha algum tipo de dado de usuário obrigatório?** → **Não**
  - *(Justificativa: câmera, microfone e arquivos de vídeo são processados e salvos 100% localmente no dispositivo; assinaturas e pagamentos são processados diretamente pela Google Play, fora de servidores próprios.)*

### B) Acesso ao app (App Access)
- **Todas as funcionalidades estão disponíveis sem restrições?** → Marque **"Todos ou alguns recursos são restritos"** e adicione uma instrução simples para o revisor:
  - **Nome:** `Acesso de teste (4 dias)`
  - **Instruções:**
    `O aplicativo não exige conta ou login. Ao abrir pela primeira vez, todos os recursos do estúdio ficam totalmente liberados por 4 dias de teste gratuito. Após 4 dias, o acesso utiliza a assinatura anual "react_studio_annual" via Google Play Billing.`

### C) Anúncios (Ads)
- **O app contém anúncios?** → **Não**

### D) Classificação de conteúdo (IARC)
- Categoria: **Utilitário / Produtividade / Ferramenta de vídeo**
- Violência, sexo, linguagem ofensiva, drogas, jogos de azar: **Não**
- Permite que usuários interajam / compartilhem conteúdo em redes externas: **Não** (o app não possui rede social própria nem chat interno)
- Permite compras digitais dentro do app: **Sim** (Assinatura anual Google Play)

### E) Público-alvo
- Faixa etária recomendada: **13–15, 16–17, 18+** (não direcionado a crianças menores de 13 anos).

### F) Recursos Gráficos prontos na pasta `/play-store/`
- **Ícone do app (512×512 PNG):** `play-store/app-icon-512x512.png`
- **Gráfico de recursos (1024×500 PNG):** `play-store/feature-graphic-1024x500.png`
- **Capturas de tela do celular:** tire 2 a 4 prints do app rodando no seu celular (tela principal com vídeo + câmera, modo Enquadrar, Mesa de Áudio e tela de Exportação/Compartilhamento) e suba na ficha da loja.
