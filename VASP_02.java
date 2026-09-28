/*
Equipe: VASP
Integrantes:
- Yan Maciel Fonseca Suzano
- Pedro de Sousa Monteiro Barbosa
- Nicoly Stefany Moura Ferreira
- Sophia Bernardo de Oliveira

ROBO 02: RISCO MINIMO E SOBREVIVENCIA

Papel na dupla: fora do duelo ele nao orbita ninguem, vai para o ponto menos
perigoso do campo. Nos nossos testes em 2x2 contra duplas de campeoes da
comunidade, esse estilo rendeu mais contra robos feitos para duelo, enquanto o
robo 01 rendeu mais contra robos feitos para melee. No 1v1 os dois usam a mesma
defesa por ondas e a mesma mira contextual. Os dois nao trocam mensagens; so
se reconhecem pelo nome (veja COLEGA DE EQUIPE).

RADAR
Em duelo ele trava no adversario, como o robo 01. Em melee ele faz o contrario
do que parece intuitivo. Em vez de seguir o alvo em quem esta atirando, ele
aponta para o inimigo que esta ha mais tempo sem ser visto. Num combate de oito
robos o perigo vem de quem a gente perdeu de vista, e nao de quem ja esta no
visor. Se ainda nao conhecemos todo mundo, ou se a informacao mais antiga
passou do limite, voltamos a girar o radar sem parar.

ESTADO POR ADVERSARIO
Guardamos uma ficha de cada inimigo com posicao, direcao, velocidade, quanto
ele esta girando, energia, distancia, quando foi visto pela ultima vez e quanto
dano ele ja nos causou, que fica no campo "ameaca". E esse historico de dano
que diferencia um inimigo que so passou perto de um que esta realmente nos
cacando.

SELECAO DE ALVO
Nao atiramos simplesmente no mais proximo. A prioridade junta cinco coisas:
quem esta mais perto de morrer, quem e mais facil de acertar por ser lento,
quem ja nos machucou, quem esta mais perto e quem foi visto mais recentemente.
Com isso o robo termina inimigos quase mortos em vez de repartir dano entre
todos e nao matar ninguem.

MOVIMENTACAO
Sao dois modos, escolhidos pelo numero de adversarios vivos.
- MELEE: nao seguimos uma orbita, escolhemos um destino. Testamos 28 direcoes
  ao redor e vamos para a menos perigosa, olhando tanto o ponto de chegada
  quanto o meio do caminho. O meio do caminho importa porque senao o robo
  atravessa por cima de um inimigo para fugir de outro. A ideia e inspirada em
  Minimum Risk Movement e Anti-Gravity, mas em vez de somar vetores de repulsao
  escolhemos um destino entre varios candidatos.
- DUELO E 2x2 (ate 2 inimigos vivos): estimamos ondas de disparo e comparamos
  continuar, inverter e frear antes da chegada das balas. O mapa de perigo
  aprende com os impactos. Sem onda confiavel: no duelo, deslocamento lateral
  com inversoes espacadas; com 2 inimigos, o movimento por risco.
No duelo, se sairmos da area segura, apontamos para o centro antes de voltar
a orbita. No melee, so aceitamos destinos dentro da area segura. Se nenhum
candidato servir, o destino escolhido e o centro do campo.

MIRA
A mira base e circular, com a curva diminuindo ao longo da viagem da bala.
A mira circular pura supoe que o inimigo vai girar no mesmo ritmo ate a bala
chegar, e isso erra feio em tiro longo. Aqui a curva vai perdendo forca, entao
de perto usamos a curva observada e de longe a previsao tende a reta.
Uma mira contextual consulta situacoes parecidas num historico limitado por
adversario. Ela considera distancia, movimento, giro e proximidade de paredes.
Avaliamos a proposta e a mira base na mesma passagem de onda, e usamos a que
vem errando menos. Com poucas amostras continuamos usando a previsao circular.
O historico contextual reinicia por batalha e persiste entre os rounds.

CONTROLE DE POTENCIA
E mais economico que o do robo 01, dividindo a energia por 9 e nao por 7,
porque em melee a batalha e longa e a energia precisa durar ate o fim.

DIFERENCA 1v1 / MELEE
Muda o radar, muda o movimento inteiro e muda o peso da selecao de alvo. A
troca e automatica pelo numero de adversarios vivos. Nos testes ele tambem
venceu todos os samples oficiais em duelo, entao o modo 1v1 nao e um modo
"de emergencia".

COLEGA DE EQUIPE
Os dois robos se reconhecem pelo nome (VASP_01 e VASP_02) ou, numa batalha de
times oficial do Robocode (arquivo .team), pela propria API (TeamRobot).
Enquanto houver algum inimigo vivo, o robo nao mira no colega, nao atira
quando o colega esta perto da linha do tiro (antes ou depois do alvo, porque
os tiros que erram seguem voando), evita chegar perto dele e conta so os
inimigos para decidir entre modo duelo e melee. No 2 contra 1 o radar fica
travado no inimigo, entao a cada 10 ticks ele da uma olhada no colega para
manter a posicao dele atualizada. Se sobrarem so os dois VASP, eles lutam
entre si normalmente. Nao ha troca de mensagens entre os robos.

INSPIRACOES (conceitos estudados na comunidade Robocode; todo o codigo foi
escrito pela equipe)
- Minimum Risk Movement e Anti-Gravity: fugir para o ponto menos perigoso.
- Wave Surfing: no duelo, desviar de balas tratando cada disparo como onda.
- Dynamic Clustering / KNN: mirar pelas situacoes mais parecidas do historico.
- Tick waves (tiros virtuais): aprender a cada tick, e nao so a cada tiro real.
- Flattener (achatamento): quando o inimigo acerta muito, evitar tambem os
  lugares onde costumamos estar, para nao deixar padrao para ele aprender.
*/
import robocode.*;
import robocode.util.Utils;
import java.util.*;

public class VASP_02 extends TeamRobot {
    /* COLEGA DE EQUIPE. O outro robo da equipe e reconhecido pelo nome
       (VASP_01 e VASP_02) ou, numa batalha de times oficial do Robocode
       (arquivo .team), pela propria API: por isso o robo estende TeamRobot.
       Enquanto houver algum inimigo vivo, o colega nao e alvo nem ameaca, nao
       atiramos com ele na linha de tiro e evitamos trombar com ele. Se so
       sobrarem os dois VASP, eles lutam normalmente, senao o round ficaria
       parado ate o limite de inatividade. Nao ha troca de mensagens. */
    private static final String PREFIXO_EQUIPE = "VASP_0";
    private boolean colegaVivo;

    // Nome sem pacote, sem versao e sem o "*" de versao de desenvolvimento:
    // "pacote.VASP_02 1.0" vira "VASP_02".
    private static String nomeBase(String nome) {
        String semVersao = nome.split(" ")[0];
        return semVersao.substring(semVersao.lastIndexOf('.') + 1).replace("*", "");
    }

    // Verdadeiro se o robo com esse nome e o outro robo da nossa equipe.
    private boolean mesmaEquipe(String nome) {
        if (isTeammate(nome))
            return true;
        String base = nomeBase(nome);
        return base.startsWith(PREFIXO_EQUIPE) && !base.equals(nomeBase(getName()));
    }

    // Colega que devemos poupar: da nossa equipe e com algum inimigo ainda vivo.
    private boolean ehColega(String nome) {
        return getOthers() > 1 && mesmaEquipe(nome);
    }

    private double colegaX, colegaY, colegaDirecao, colegaVelocidade;
    private long colegaVisto = -100;
    // Distancia minima que tentamos manter do colega para nao trombar com ele.
    private static final double DISTANCIA_COLEGA = 110;

    /* Onde o colega deve estar agora. Quando o radar trava num inimigo (duelo
       no fim do round) paramos de ver o colega; entao estimamos a posicao
       dele seguindo em linha reta a partir da ultima leitura, por ate 20 ticks. */
    private double colegaEstimadoX() {
        long dt = Math.min(20, getTime() - colegaVisto);
        return limitar(colegaX + colegaVelocidade * Math.sin(colegaDirecao) * dt, 18,
                getBattleFieldWidth() - 18);
    }

    private double colegaEstimadoY() {
        long dt = Math.min(20, getTime() - colegaVisto);
        return limitar(colegaY + colegaVelocidade * Math.cos(colegaDirecao) * dt, 18,
                getBattleFieldHeight() - 18);
    }

    // Temos informacao do colega recente o bastante para confiar (30 ticks)
    // e ainda existe inimigo em campo (senao o colega virou adversario).
    private boolean colegaConhecido() {
        return getTime() - colegaVisto <= 30 && getOthers() > 1;
    }

    /* Verdadeiro se este tiro pode acertar o colega: ele esta perto da linha
       do disparo, ANTES ou DEPOIS do alvo. A maioria dos tiros erra o alvo e
       continua voando, entao um colega do outro lado do inimigo tambem corre
       risco. A margem cresce com o tempo desde que o vimos e com a distancia. */
    private boolean colegaNaLinha(double anguloTiro, double distanciaAlvo) {
        if (!colegaConhecido())
            return false;
        double cx = colegaEstimadoX(), cy = colegaEstimadoY();
        double distanciaColega = Math.hypot(cx - getX(), cy - getY());
        double desvio = Utils.normalRelativeAngle(Math.atan2(cx - getX(), cy - getY()) - anguloTiro);
        if (Math.abs(desvio) > Math.PI / 2)
            return false;
        double margem = 26 + 4 * (getTime() - colegaVisto) + 0.04 * distanciaColega;
        return Math.abs(Math.sin(desvio)) * distanciaColega < margem;
    }

    // Verdadeiro se um ponto do campo fica longe o bastante do colega.
    // Sem colega conhecido e sempre verdadeiro.
    private boolean longeDoColega(double x, double y) {
        if (!colegaConhecido())
            return true;
        return Math.hypot(x - colegaEstimadoX(), y - colegaEstimadoY()) > DISTANCIA_COLEGA;
    }

    // Quantos adversarios de verdade ainda estao vivos (getOthers() conta
    // tambem o colega). Se so resta o colega, ele conta como adversario.
    private int adversariosVivos() {
        return colegaVivo && getOthers() > 1 ? getOthers() - 1 : getOthers();
    }

    // Parametros reunidos no topo para facilitar os ajustes.
    private static final double MARGEM_CAMPO = 48, DISTANCIA_MOVIMENTO = 135, DISTANCIA_ORBITA = 310;
    private static final int VALIDADE_SCAN = 30; // acima disso a ficha do inimigo e velha
    private static final int DIRECOES_RISCO = 28; // quantas direcoes testamos por replanejamento
    private static final int INTERVALO_PLANEJAMENTO = 21; // de quantos em quantos ticks escolhemos novo destino
    private static final double ALCANCE_PLANEJAMENTO = 155; // a que distancia procuramos o proximo destino

    private final Map<String, EstadoInimigo> inimigos = new LinkedHashMap<String, EstadoInimigo>();
    private EstadoInimigo alvoAtual;
    private double destinoX, destinoY;
    private int ladoMovimento = 1; // 1 ou -1: lado do deslocamento no duelo
    private long proximaMudancaDirecao = 50, colisaoAte;
    private long proximoPlanejamento;
    private double anteriorX, anteriorY; // de onde saimos no ultimo destino

    // Ficha de um adversario, atualizada a cada vez que o radar o encontra.
    private static class EstadoInimigo {
        String nome;
        double x, y, direcao, velocidade, rotacao, energia, distancia;
        double ameaca; // dano que este inimigo ja nos causou
        long ultimoScan = -1;
    }

    public void run() {
        String[] colegas = getTeammates();
        colegaVivo = colegas != null && colegas.length > 0;
        // No comeco da batalha (round 0) zeramos o que foi aprendido, para uma
        // batalha nao contaminar a seguinte. Nos rounds 1 a 4 o aprendizado continua.
        if (getRoundNum() == 0) {
            perfisDefesa.clear();
            contextos.clear();
        }
        // Radar, canhao e corpo independentes, para que virar o corpo nao arraste
        // a mira junto.
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);
        setColors(new java.awt.Color(35, 155, 100), java.awt.Color.DARK_GRAY, java.awt.Color.ORANGE);
        destinoX = getX();
        destinoY = getY();
        while (true) {
            escolherAlvo();
            atualizarRadar();
            atualizarMovimento();
            mirarEAtirar();
            execute();
        }
    }

    // Atualiza a ficha de quem o radar acabou de ver.
    public void onScannedRobot(ScannedRobotEvent event) {
        // O colega de equipe nao e alvo nem ameaca enquanto houver inimigo vivo.
        if (ehColega(event.getName())) {
            colegaVivo = true;
            // Guardamos onde o colega esta para nao atirar com ele na frente.
            double anguloColega = getHeadingRadians() + event.getBearingRadians();
            colegaX = getX() + event.getDistance() * Math.sin(anguloColega);
            colegaY = getY() + event.getDistance() * Math.cos(anguloColega);
            colegaDirecao = event.getHeadingRadians();
            colegaVelocidade = event.getVelocity();
            colegaVisto = getTime();
            return;
        }
        defesa.observar(event);
        miraContextual.observar(event);
        EstadoInimigo o = inimigos.get(event.getName());
        if (o == null) {
            o = new EstadoInimigo();
            o.nome = event.getName();
            inimigos.put(o.nome, o);
        }

        /* Quanto ele girou por tick desde o ultimo scan. Em melee os scans sao
           espacados e a medida vem ruidosa, entao suavizamos com uma media entre o
           valor antigo e o novo em vez de confiar so na ultima leitura. */
        long ticksDecorridos = getTime() - o.ultimoScan;
        double giroMedido = o.ultimoScan >= 0 && ticksDecorridos > 0
                ? Utils.normalRelativeAngle(event.getHeadingRadians() - o.direcao) / ticksDecorridos
                : 0;
        o.rotacao = 0.5 * o.rotacao + 0.5 * giroMedido;

        /* Converte a leitura relativa em coordenadas absolutas do campo. No Robocode o
           angulo comeca no norte e cresce no sentido horario, entao x usa seno e y usa
           cosseno. */
        double anguloAbsoluto = getHeadingRadians() + event.getBearingRadians();
        o.x = getX() + event.getDistance() * Math.sin(anguloAbsoluto);
        o.y = getY() + event.getDistance() * Math.cos(anguloAbsoluto);

        o.velocidade = event.getVelocity();
        o.direcao = event.getHeadingRadians();
        o.energia = event.getEnergy();
        o.distancia = event.getDistance();
        o.ultimoScan = getTime();
    }

    /* Escolhe o alvo. Quanto maior a prioridade, melhor o alvo.
       - paraFinalizar cresce quando o inimigo tem pouca energia, porque vale a pena
         matar.
       - facilidadeAcerto cai quando o inimigo e rapido, porque e mais dificil de
         acertar.
       - ameaca da bonus para quem ja nos causou dano.
       - O divisor penaliza quem esta longe e quem foi visto ha muito tempo. */
    private void escolherAlvo() {
        double maiorPrioridade = -1;
        EstadoInimigo escolhido = null;
        for (EstadoInimigo o : inimigos.values()) {
            long idadeScan = getTime() - o.ultimoScan;
            if (idadeScan > VALIDADE_SCAN)
                continue; // informacao velha demais, ignora
            o.distancia = Math.hypot(o.x - getX(), o.y - getY());
            double paraFinalizar = 1 + 16 / (o.energia + 8);
            double facilidadeAcerto = 1.4 - Math.abs(o.velocidade) / 16;
            double prioridade = paraFinalizar * facilidadeAcerto * (1 + Math.min(0.4, o.ameaca / 100))
                    / ((o.distancia + 120) * (1 + idadeScan / 4.0));
            // O alvo atual ganha bonus, porque trocar de alvo custa o tempo de girar
            // o canhao de novo. Assim so trocamos se o outro for bem melhor.
            if (o == alvoAtual)
                prioridade *= 1.2;
            if (prioridade > maiorPrioridade) {
                maiorPrioridade = prioridade;
                escolhido = o;
            }
        }
        alvoAtual = escolhido;
    }

    /* No 2 contra 1 o radar fica travado no inimigo e paramos de ver o colega.
       Quando faz mais de 10 ticks que nao o vemos, damos uma olhada rapida
       para onde ele deve estar; se faz mais de 25, damos uma volta completa.
       Assim o desvio do colega e a checagem de linha de tiro continuam valendo. */
    private boolean radarNoColega() {
        long semVer = getTime() - colegaVisto;
        if (!colegaVivo || adversariosVivos() != 1 || getOthers() <= 1 || semVer <= 10)
            return false;
        if (semVer > 25) {
            // Perdemos o colega de vista ha muito tempo: uma volta completa acha ele.
            setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
            return true;
        }
        double diferenca = Utils.normalRelativeAngle(
                Math.atan2(colegaEstimadoX() - getX(), colegaEstimadoY() - getY()) - getRadarHeadingRadians());
        setTurnRadarRightRadians(diferenca + Math.copySign(0.3, diferenca));
        return true;
    }

    private void atualizarRadar() {
        if (radarNoColega())
            return;
        if (adversariosVivos() == 1 && alvoAtual != null && getTime() - alvoAtual.ultimoScan < 3) {
            // No duelo trava no alvo. O fator 1.9 faz o radar passar do ponto, o que
            // garante que ele cruze o inimigo mesmo que o inimigo tenha se movido.
            double diferenca = Utils
                    .normalRelativeAngle(anguloAte(alvoAtual.x, alvoAtual.y) - getRadarHeadingRadians());
            setTurnRadarRightRadians(diferenca * 1.9 + Math.copySign(0.04, diferenca));
        } else {
            // No melee procuramos quem esta ha mais tempo sem ser visto.
            EstadoInimigo maisAntigo = null;
            for (EstadoInimigo o : inimigos.values())
                if (maisAntigo == null || o.ultimoScan < maisAntigo.ultimoScan)
                    maisAntigo = o;

            /* Giro completo se ainda nao conhecemos todo mundo ou se a informacao mais
               antiga ja passou do aceitavel. E assim que o radar se recupera sozinho
               quando perde o mapa do campo. */
            if (maisAntigo == null || inimigos.size() < adversariosVivos() || getTime() - maisAntigo.ultimoScan > 16) {
                setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
            } else {
                /* Aponta para ele e varre uma faixa em volta. A faixa cresce com o
                   tempo sem ve-lo, porque quanto mais tempo passa, mais longe da ultima
                   posicao conhecida ele pode estar. */
                double diferenca = Utils
                        .normalRelativeAngle(anguloAte(maisAntigo.x, maisAntigo.y) - getRadarHeadingRadians());
                double aberturaVarredura = Math.atan2(30 + 8 * (getTime() - maisAntigo.ultimoScan),
                        Math.max(40, maisAntigo.distancia));
                setTurnRadarRightRadians(diferenca + Math.copySign(aberturaVarredura, diferenca));
            }
        }
    }

    // Decide qual dos dois modos de movimento usar neste tick.
    private void atualizarMovimento() {
        // Com ate 2 inimigos, se ha onda de disparo a caminho (e nenhuma manobra de colisao
        // em andamento), o modulo de defesa decide o movimento neste tick.
        if (getTime() >= colisaoAte && defesa.mover())
            return;
        // Depois de uma colisao deixamos a manobra de escape terminar antes de
        // voltar a planejar, senao sobrescreveriamos o comando no tick seguinte.
        if (getTime() < colisaoAte)
            return;

        // Com varios inimigos usamos o movimento por risco, e com um so o deslocamento
        // lateral.
        if (adversariosVivos() > 1 && !inimigos.isEmpty()) {
            movimentoPorRisco();
            return;
        }

        // Pelo mesmo motivo do robo 01 no duelo, saimos da faixa da parede antes de
        // voltar a orbita.
        if (!dentroAreaSegura(getX(), getY())) {
            seguirRumo(anguloAte(getBattleFieldWidth() / 2, getBattleFieldHeight() / 2), 170);
            return;
        }

        // Inverte o lado em intervalos sorteados, para nao virar um padrao.
        if (getTime() >= proximaMudancaDirecao) {
            ladoMovimento = -ladoMovimento;
            proximaMudancaDirecao = getTime() + 40 + Utils.getRandom().nextInt(40);
        }

        /* Rumo perpendicular ao alvo com correcao de distancia. Fecha o angulo para
           aproximar quando estamos longe e abre para afastar quando estamos perto
           demais. */
        double rumo = alvoAtual == null ? anguloAte(getBattleFieldWidth() / 2, getBattleFieldHeight() / 2)
                : anguloAte(alvoAtual.x, alvoAtual.y) + ladoMovimento
                        * (Math.PI / 2 + limitar((DISTANCIA_ORBITA - alvoAtual.distancia) / 250, -0.3, 0.55));

        double melhor = Double.POSITIVE_INFINITY, escolhido = rumo;
        /* Projecoes curtas impedem que o comando orbital termine na parede.
           Procuramos o rumo mais proximo do desejado que ainda caia dentro do campo,
           testando desvios para os dois lados. */
        for (int i = -12; i <= 12; i++) {
            double a = rumo + i * Math.PI / 24;
            double x = getX() + DISTANCIA_MOVIMENTO * Math.sin(a), y = getY() + DISTANCIA_MOVIMENTO * Math.cos(a);
            if (!dentroAreaSegura(x, y) || !longeDoColega(x, y))
                continue;
            double custo = Math.abs(i);
            if (custo < melhor) {
                melhor = custo;
                escolhido = a;
            }
        }
        if (!Double.isFinite(melhor))
            escolhido = anguloAte(getBattleFieldWidth() / 2, getBattleFieldHeight() / 2);
        seguirRumo(escolhido, 170);
    }

    /* Movimento de melee. Em vez de orbitar alguem, escolhemos para onde ir. So
       replanejamos de tempos em tempos ou quando chegamos, porque replanejar a cada
       poucos ticks faz o robo trocar de ideia antes de sair do lugar, e ele acaba
       oscilando sem sair de perto do perigo. */
    private void movimentoPorRisco() {
        double distanciaRestante = Math.hypot(destinoX - getX(), destinoY - getY());
        if (getTime() >= proximoPlanejamento || distanciaRestante < 30) {
            double melhor = Double.POSITIVE_INFINITY, escolhidoX = getBattleFieldWidth() / 2,
                    escolhidoY = getBattleFieldHeight() / 2;
            for (int i = 0; i < DIRECOES_RISCO; i++) {
                double a = i * Math.PI * 2 / DIRECOES_RISCO;
                double x = getX() + ALCANCE_PLANEJAMENTO * Math.sin(a), y = getY() + ALCANCE_PLANEJAMENTO * Math.cos(a);
                if (!dentroAreaSegura(x, y))
                    continue;

                // O caminho e a chegada precisam ser seguros, assim nao cruzamos um
                // rammer para fugir de outro.
                double custo = calcularRiscoPonto(x, y, a)
                        + 0.45 * calcularRiscoPonto((x + getX()) / 2, (y + getY()) / 2, a);

                // Penalidade por voltar para perto de onde ja estavamos. Sem isso o
                // robo fica indo e voltando entre dois pontos.
                double afastamentoAnterior = Math.hypot(x - anteriorX, y - anteriorY);
                custo += 0.20 / (1 + afastamentoAnterior / 75);

                // Com uma pequena inercia mantemos o rumo atual quando os destinos
                // empatam, em vez de ziguezaguear a toa.
                if (distanciaRestante > 30)
                    custo += 0.06 * Math.abs(Utils.normalRelativeAngle(a - anguloAte(destinoX, destinoY)));

                if (custo < melhor) {
                    melhor = custo;
                    escolhidoX = x;
                    escolhidoY = y;
                }
            }
            anteriorX = getX();
            anteriorY = getY();
            destinoX = escolhidoX;
            destinoY = escolhidoY;
            proximoPlanejamento = getTime() + INTERVALO_PLANEJAMENTO;
        }
        seguirRumo(anguloAte(destinoX, destinoY), Math.max(40, Math.hypot(destinoX - getX(), destinoY - getY())));
    }

    /* Quanto um ponto do campo e perigoso. Somamos dois tipos de risco.
       - Parede: cresce rapido perto da borda, porque ali perdemos manobra e ficamos
         faceis de encurralar.
       - Inimigos: cada um empurra com forca proporcional a energia dele, ja que quem
         tem mais vida atira mais forte, e ao dano que ja nos causou. Essa forca cai com
         o quadrado da distancia.
       O termo componenteRadial penaliza ir em direcao a um inimigo ou fugir na linha
       reta dele. Nos dois casos a nossa velocidade lateral vira quase zero e o tiro
       dele fica facil. */
    private double calcularRiscoPonto(double x, double y, double rumoCandidato) {
        double folgaParede = Math.min(Math.min(x, getBattleFieldWidth() - x), Math.min(y, getBattleFieldHeight() - y));
        double custo = 0.16 * Math.pow(70 / Math.max(18, folgaParede), 2);
        for (EstadoInimigo o : inimigos.values()) {
            if (getTime() - o.ultimoScan > VALIDADE_SCAN)
                continue;
            double distancia = Math.hypot(x - o.x, y - o.y);
            double componenteRadial = Math.cos(rumoCandidato - Math.atan2(o.x - getX(), o.y - getY()));
            double forcaAmeaca = (0.65 + Math.min(100, o.energia) / 140 + Math.min(0.5, o.ameaca / 140));
            custo += forcaAmeaca * (1 + 0.45 * componenteRadial * componenteRadial)
                    * Math.pow(210 / Math.max(36, distancia), 2);
        }
        // Ficar perto do colega tambem custa: evita trombadas e que um fique
        // na linha de tiro do outro.
        if (colegaConhecido()) {
            double distanciaColega = Math.hypot(x - colegaEstimadoX(), y - colegaEstimadoY());
            custo += 0.7 * Math.pow(150 / Math.max(36, distanciaColega), 2);
        }
        return custo;
    }

    /* Mira e tiro. Simula para onde o alvo vai enquanto a bala viaja, usando a curva
       que ele vinha fazendo, mas com essa curva perdendo forca ao longo do caminho. */
    private void mirarEAtirar() {
        if (alvoAtual == null || getTime() - alvoAtual.ultimoScan > 3)
            return; // sem informacao fresca, nao atira
        EstadoInimigo o = alvoAtual;

        /* Potencia por faixa de distancia, limitada pelo quanto basta para finalizar o
           inimigo e por uma fatia da nossa energia. O divisor 9 e mais economico que o
           do robo 01, porque aqui a batalha costuma durar mais. */
        double potencia = o.distancia < 200 ? 2.9 : o.distancia < 430 ? 2.2 : 1.4;
        double paraFinalizar = o.energia <= 4 ? o.energia / 4 : (o.energia + 2) / 6;
        potencia = limitar(Math.min(potencia, Math.min(paraFinalizar, getEnergy() / 9)), 0.1, 3);

        double x = o.x, y = o.y, h = o.direcao, velocidadeBala = Rules.getBulletSpeed(potencia);
        long idadeScan = getTime() - o.ultimoScan; // quantos ticks a ficha ja tem
        for (int t = 0; t < 95; t++) {
            // O 0.97^t e o amortecimento que faz a curva prevista ir sumindo. Perto
            // vale a curva observada, e longe a previsao vira quase uma reta.
            h += limitar(o.rotacao, -0.1, 0.1) * Math.pow(0.97, t);
            x = limitar(x + o.velocidade * Math.sin(h), 18, getBattleFieldWidth() - 18);
            y = limitar(y + o.velocidade * Math.cos(h), 18, getBattleFieldHeight() - 18);
            // Comecamos a contar o voo da bala so depois de recuperar os ticks
            // que a ficha ja estava atrasada, senao mirariamos adiantado.
            if (t > idadeScan && (t - idadeScan) * velocidadeBala >= Math.hypot(x - getX(), y - getY()))
                break;
        }

        // A mira contextual pode trocar o angulo circular, se ela vier acertando mais.
        double anguloEscolhido = miraContextual.escolher(o.nome, potencia, anguloAte(x, y));
        double erroMira = Utils.normalRelativeAngle(anguloEscolhido - getGunHeadingRadians());
        setTurnGunRightRadians(erroMira);
        // So atira com o canhao frio, ja alinhado dentro da largura angular do alvo
        // e com energia sobrando depois do tiro.
        if (getGunHeat() == 0 && Math.abs(erroMira) < Math.atan2(17, o.distancia) && getEnergy() > potencia + 0.4
                && !colegaNaLinha(anguloEscolhido, o.distancia)) {
            // Registramos o tiro so se a bala realmente saiu.
            if (setFireBullet(potencia) != null)
                miraContextual.registrar(o.nome, potencia);
        }
    }

    /* Transforma um rumo absoluto em comandos de virar e andar. Se o destino esta
       atras, andamos de re, porque girar o corpo inteiro demora e nos deixa lentos e
       faceis de acertar durante a virada. */
    private void seguirRumo(double rumoAbsoluto, double distanciaAndar) {
        double giroNecessario = Utils.normalRelativeAngle(rumoAbsoluto - getHeadingRadians());
        double sentido = Math.cos(giroNecessario) >= 0 ? 1 : -1;
        if (sentido < 0)
            giroNecessario = Utils.normalRelativeAngle(giroNecessario + Math.PI);
        setTurnRightRadians(giroNecessario);
        setAhead(sentido * distanciaAndar);
    }

    // Verdadeiro se o ponto esta dentro do campo com folga para a parede.
    private boolean dentroAreaSegura(double x, double y) {
        return x > MARGEM_CAMPO && y > MARGEM_CAMPO && x < getBattleFieldWidth() - MARGEM_CAMPO
                && y < getBattleFieldHeight() - MARGEM_CAMPO;
    }

    // Angulo absoluto da nossa posicao ate um ponto do campo.
    private double anguloAte(double x, double y) {
        return Math.atan2(x - getX(), y - getY());
    }

    // Prende um valor entre um minimo e um maximo.
    private static double limitar(double valor, double minimo, double maximo) {
        if (valor < minimo)
            return minimo;
        return valor > maximo ? maximo : valor;
    }

    // Quando levamos um tiro, anotamos o dano na ficha de quem atirou. E esse
    // numero que faz o movimento fugir mais de quem esta realmente nos cacando.
    public void onHitByBullet(HitByBulletEvent event) {
        defesa.impacto(event.getBullet(),true);
        EstadoInimigo o = inimigos.get(event.getName());
        if (o != null)
            o.ameaca += Rules.getBulletDamage(event.getPower());
    }

    // Quando acertamos alguem, o evento ja informa a energia que sobrou para ele,
    // o que e mais atual do que esperar o proximo scan.
    public void onBulletHit(BulletHitEvent event) {
        defesa.nossoAcerto(event);
        EstadoInimigo o = inimigos.get(event.getName());
        if (o != null) {
            o.energia = event.getEnergy();
        }
    }

    // Quando alguem morre, tiramos do mapa para nao mirar em fantasma nem contar
    // o perigo de quem nao existe mais.
    public void onRobotDeath(RobotDeathEvent event) {
        if (mesmaEquipe(event.getName())) {
            colegaVivo = false;
            colegaVisto = -100;
        }
        miraContextual.remover(event.getName());
        inimigos.remove(event.getName());
        if (alvoAtual != null && alvoAtual.nome.equals(event.getName()))
            alvoAtual = null;
    }

    // Bateu na parede: inverte o lado e volta para o centro. O colisaoAte
    // segura o planejamento por alguns ticks para a manobra terminar.
    public void onHitWall(HitWallEvent event) {
        setMaxVelocity(8);
        ladoMovimento = -ladoMovimento;
        seguirRumo(anguloAte(getBattleFieldWidth() / 2, getBattleFieldHeight() / 2), 110);
        colisaoAte = getTime() + 12;
    }

    // Bateu em outro robo: inverte o lado e afasta na direcao oposta a dele.
    public void onHitRobot(HitRobotEvent event) {
        setMaxVelocity(8);
        defesa.colisao(event);
        ladoMovimento = -ladoMovimento;
        seguirRumo(getHeadingRadians() + event.getBearingRadians() + Math.PI, 100);
        colisaoAte = getTime() + 10;
    }

    // Nada de imprimir durante a batalha: escrever no console custa tempo e
    // faria o robo perder ainda mais turnos.
    public void onSkippedTurn(SkippedTurnEvent event) {
        // Contabilizado pelo engine.
    }

    // =====================================================================
    // MODULO DE DEFESA POR ONDAS (usado com ate 2 inimigos vivos)
    // Inspiracao: conceito de "Wave Surfing" da comunidade Robocode. O codigo
    // foi escrito pela equipe. Ideia: quando o inimigo atira, a energia dele
    // cai entre 0.1 e 3. Tratamos cada queda como uma "onda" circular que sai
    // da posicao dele e cresce na velocidade da bala. Como nao enxergamos as
    // balas, desviamos das ondas: simulamos nossas opcoes de movimento e
    // escolhemos a que cruza a onda no ponto menos perigoso.
    // =====================================================================
    // BINS_DEFESA: em quantas faixas dividimos o angulo de fuga para medir perigo.
    // LIMITE_ONDAS: maximo de ondas acompanhadas ao mesmo tempo.
    private static final int BINS_DEFESA = 41, LIMITE_ONDAS = 14;
    // Com ate 2 inimigos (o caso do 2x2 com o colega vivo) o radar consegue
    // rever cada um a cada 1 ou 2 ticks, entao a deteccao de disparos pela queda
    // de energia continua confiavel. Com mais inimigos ela vira chute.
    private static final int INIMIGOS_ONDAS = 2;
    // SEGMENTOS_DETALHE = 2 distancias x 3 velocidades laterais x 3 aceleracoes x 2 parede.
    private static final int SEGMENTOS_DETALHE = 36;
    // Perfil de perigo por adversario. E static para durar entre os rounds da
    // mesma batalha; e zerado no round 0.
    private static final Map<String, PerfilDefesa> perfisDefesa =
            new HashMap<String, PerfilDefesa>();
    private final DefesaOndas defesa = new DefesaOndas();

    // Onde as balas de um adversario nos acertaram. Linha 0 = tiros de perto
    // (menos de 400 px), linha 1 = de longe. Cada linha divide o fator de
    // desvio (-1 a +1) em BINS_DEFESA faixas.
    private static class PerfilDefesa {
        double[][] perigo = new double[2][BINS_DEFESA];
        int impactos;
        // Perfil detalhado: separa os impactos pela nossa situacao no momento do
        // tiro (distancia, velocidade lateral, aceleracao e parede). Uma mira que
        // aprende enxerga essas diferencas, entao nossa defesa tambem precisa.
        double[][] detalhado = new double[SEGMENTOS_DETALHE][BINS_DEFESA];
        int[] impactosDetalhe = new int[SEGMENTOS_DETALHE];
        // Onde estavamos quando cada onda passou, acertando ou nao. Se o inimigo
        // acerta muito, fugimos tambem dos lugares onde costumamos estar, para
        // nao deixar um padrao que uma mira adaptativa consiga aprender.
        double[] passagens = new double[BINS_DEFESA];
        int ondasPassadas;
    }

    // Ultima leitura de um adversario, usada para notar quedas de energia.
    // proximoTiro: primeiro tick em que o canhao dele pode estar frio de novo.
    // ambiguoAte: ate este tick uma queda pode ser colisao, e nao disparo.
    private static class EnergiaObservada {
        double x, y, energia, velocidade;
        long tick = -1, proximoTiro, ambiguoAte = -1;
        PerfilDefesa perfil;
    }

    // Um disparo inimigo estimado: de onde saiu, em que tick, a velocidade da
    // bala e o angulo de referencia (a direcao ate nos no momento da mira).
    private static class OndaInimiga {
        String nome;
        double x, y, velocidade, referencia, escape;
        long disparo;
        int lado, segmento, detalhe;
        boolean passagemRegistrada;
        PerfilDefesa perfil;
    }

    /* Guarda nossas ultimas posicoes, detecta disparos pela queda de energia,
       aprende onde fomos atingidos e decide o movimento, com ate 2 inimigos, comparando
       tres opcoes: continuar no sentido atual, inverter ou frear. A onda e uma
       estimativa, porque nao temos acesso a posicao real das balas em voo. */
    private class DefesaOndas {
        final Map<String, EnergiaObservada> energia = new HashMap<String, EnergiaObservada>();
        final List<OndaInimiga> ondas = new ArrayList<OndaInimiga>();
        final double[][] posicoes = new double[6][4];
        final long[] instantes = {-1, -1, -1, -1, -1, -1};
        int sentido = 1;

        // Guarda nossa posicao, direcao e velocidade dos ultimos 6 ticks. O
        // inimigo mira usando onde estavamos 2 ticks antes de notarmos a queda
        // de energia, entao precisamos lembrar dessa posicao.
        void guardar(RobotStatus estado) {
            int i = (int) (estado.getTime() % posicoes.length);
            instantes[i] = estado.getTime();
            posicoes[i][0] = estado.getX();
            posicoes[i][1] = estado.getY();
            posicoes[i][2] = estado.getHeadingRadians();
            posicoes[i][3] = estado.getVelocity();
        }

        /* Chamado a cada scan. Se a energia do inimigo caiu entre 0.1 e 3 e nada
           mais explica a queda (colisao, parede ou bala nossa), consideramos que
           ele atirou e criamos uma onda. So fazemos isso com ate 2 inimigos
           vivos (duelo ou 2x2 com o colega) e com scans de no maximo 2 ticks de
           intervalo: com mais inimigos as leituras ficam espacadas demais e a
           deteccao nao seria confiavel. */
        void observar(ScannedRobotEvent e) {
            EnergiaObservada anterior = energia.get(e.getName());
            if (anterior == null) {
                anterior = new EnergiaObservada();
                energia.put(e.getName(), anterior);
                anterior.perfil = perfisDefesa.get(e.getName());
                if (anterior.perfil == null) {
                    anterior.perfil = new PerfilDefesa();
                    perfisDefesa.put(e.getName(), anterior.perfil);
                }
            }
            long agora = getTime();
            double queda = anterior.energia - e.getEnergy();
            // Bater na parede tambem tira energia. Se ele parou colado na parede,
            // descontamos da queda o dano estimado da batida.
            boolean pertoParede =
                    Math.min(
                                    Math.min(anterior.x, getBattleFieldWidth() - anterior.x),
                                    Math.min(anterior.y, getBattleFieldHeight() - anterior.y))
                            < 28;
            if (pertoParede && Math.abs(e.getVelocity()) < 0.01)
                queda -= Math.max(0, Math.abs(anterior.velocidade) / 2 - 1);
            int historico = (int) (Math.max(0, agora - 2) % posicoes.length);
            // Todas as condicoes abaixo precisam valer para a queda contar como
            // disparo: ate 2 inimigos, scan recente, sem colisao recente, canhao
            // dele ja frio, queda entre 0.1 e 3, nossa posicao conhecida e ele
            // nao colado em nos.
            if (adversariosVivos() <= INIMIGOS_ONDAS
                    && agora - anterior.tick <= 2
                    && agora > anterior.ambiguoAte
                    && agora >= anterior.proximoTiro
                    && queda >= 0.0999
                    && queda <= 3.0001
                    && instantes[historico] == agora - 2
                    && e.getDistance() > 65) {
                OndaInimiga onda = new OndaInimiga();
                onda.nome = e.getName();
                onda.x = anterior.x;
                onda.y = anterior.y;
                onda.disparo = agora - 1;
                onda.velocidade = Rules.getBulletSpeed(limitar(queda, 0.1, 3));
                onda.referencia =
                        Math.atan2(
                                posicoes[historico][0] - onda.x, posicoes[historico][1] - onda.y);
                onda.lado =
                        posicoes[historico][3] * Math.sin(posicoes[historico][2] - onda.referencia)
                                        < 0
                                ? -1
                                : 1;
                onda.escape = Math.asin(8 / onda.velocidade);
                onda.segmento = e.getDistance() < 400 ? 0 : 1;
                onda.detalhe = segmentoDetalhado(historico, onda);
                onda.perfil = anterior.perfil;
                if (ondas.size() == LIMITE_ONDAS) ondas.remove(0);
                ondas.add(onda);
                // Depois de atirar o canhao dele esquenta. Nenhuma queda antes de
                // esfriar pode ser outro tiro.
                anterior.proximoTiro =
                        agora + (long) Math.ceil(Rules.getGunHeat(queda) / getGunCoolingRate()) - 1;
            }
            // Atualiza a ficha com a leitura atual.
            double angulo = getHeadingRadians() + e.getBearingRadians();
            anterior.x = getX() + e.getDistance() * Math.sin(angulo);
            anterior.y = getY() + e.getDistance() * Math.cos(angulo);
            anterior.energia = e.getEnergy();
            anterior.velocidade = e.getVelocity();
            anterior.tick = agora;
        }

        // Nossa bala acertou: parte da queda de energia dele foi dano nosso.
        /* Nossa situacao no tick em que o inimigo mirou, em 36 grupos:
           distancia (perto/longe), velocidade lateral (parado/media/rapida),
           aceleracao (freando/constante/acelerando) e parede proxima ou nao. */
        int segmentoDetalhado(int historico, OndaInimiga onda) {
            double velocidade = Math.abs(posicoes[historico][3]);
            double lateral = Math.abs(velocidade * Math.sin(posicoes[historico][2] - onda.referencia));
            int faixaLateral = lateral < 2 ? 0 : lateral < 6 ? 1 : 2;
            int anterior = (historico + posicoes.length - 1) % posicoes.length;
            int aceleracao = 1;
            if (instantes[anterior] == instantes[historico] - 1) {
                double antes = Math.abs(posicoes[anterior][3]);
                if (velocidade < antes - 0.5) aceleracao = 0;
                else if (velocidade > antes + 0.5) aceleracao = 2;
            }
            double x = posicoes[historico][0], y = posicoes[historico][1];
            double parede = Math.min(Math.min(x, getBattleFieldWidth() - x),
                    Math.min(y, getBattleFieldHeight() - y));
            int faixaParede = parede < 110 ? 1 : 0;
            return ((onda.segmento * 3 + faixaLateral) * 3 + aceleracao) * 2 + faixaParede;
        }

        void nossoAcerto(BulletHitEvent e) {
            EnergiaObservada c = energia.get(e.getName());
            // Subtrair o dano conhecido preserva uma eventual queda causada por
            // disparo no mesmo tick; substituir pela energia do evento a esconderia.
            if (c != null) c.energia -= Rules.getBulletDamage(e.getBullet().getPower());
        }

        // Colisao tambem tira energia. Usamos a energia informada pelo evento e
        // marcamos o tick como ambiguo para nao confundir com disparo.
        void colisao(HitRobotEvent e) {
            EnergiaObservada c = energia.get(e.getName());
            if (c != null) {
                c.energia = e.getEnergy();
                c.ambiguoAte = getTime() + 1;
            }
        }

        /* Uma bala inimiga nos acertou (acertou = true) ou bateu numa bala nossa
           (acertou = false). Quando ele nos acerta ganha energia de bonus, entao
           somamos esse bonus para nao estragar a deteccao da proxima queda.
           Depois procuramos a onda dessa bala: mesmo atirador, mesma velocidade e
           raio compativel com a posicao da bala. Se nos acertou, marcamos no
           perfil de perigo o ponto do impacto, espalhado numa curva suave. Nos
           dois casos a onda sai da lista, porque o destino da bala ja e conhecido. */
        void impacto(Bullet bala, boolean acertou) {
            EnergiaObservada c = energia.get(bala.getName());
            if (acertou && c != null) c.energia += Rules.getBulletHitBonus(bala.getPower());
            OndaInimiga correspondente = null;
            double menorErro = 32;
            for (OndaInimiga o : ondas) {
                double erro =
                        Math.abs(
                                Math.hypot(bala.getX() - o.x, bala.getY() - o.y)
                                        - (getTime() - o.disparo) * o.velocidade);
                if (o.nome.equals(bala.getName())
                        && Math.abs(o.velocidade - bala.getVelocity()) < 0.01
                        && erro < menorErro) {
                    correspondente = o;
                    menorErro = erro;
                }
            }
            if (correspondente == null) return;
            if (acertou) {
                OndaInimiga o = correspondente;
                double fator =
                        Utils.normalRelativeAngle(
                                        Math.atan2(bala.getX() - o.x, bala.getY() - o.y)
                                                - o.referencia)
                                / o.escape
                                * o.lado;
                double centro = (limitar(fator, -1, 1) + 1) * (BINS_DEFESA - 1) / 2;
                for (int i = 0; i < BINS_DEFESA; i++) {
                    double desvio = (i - centro) / 2;
                    double marca = Math.exp(-desvio * desvio / 2);
                    o.perfil.perigo[o.segmento][i] = 0.96 * o.perfil.perigo[o.segmento][i] + marca;
                    o.perfil.detalhado[o.detalhe][i] = 0.9 * o.perfil.detalhado[o.detalhe][i] + marca;
                }
                o.perfil.impactos++;
                o.perfil.impactosDetalhe[o.detalhe]++;
            }
            ondas.remove(correspondente);
        }

        /* Decide o movimento com ate 2 inimigos. Pega as duas ondas que chegam primeiro e
           simula tres opcoes ate elas passarem: continuar, inverter e frear.
           Inverter so vence se for pelo menos 3% melhor, e frear so se for 10%
           melhor e estivermos longe do atirador, para nao trocar de ideia por
           diferencas minimas. Retorna false quando nao ha onda, e ai o movimento
           normal do robo assume. */
        boolean mover() {
            setMaxVelocity(8);
            if (adversariosVivos() > INIMIGOS_ONDAS) {
                ondas.clear();
                return false;
            }
            // Descarta ondas que ja passaram e acha as duas mais proximas de nos.
            OndaInimiga primeira = null, segunda = null;
            double chegada1 = 1e9, chegada2 = 1e9;
            for (Iterator<OndaInimiga> it = ondas.iterator(); it.hasNext(); ) {
                OndaInimiga o = it.next();
                double restante =
                        Math.hypot(getX() - o.x, getY() - o.y)
                                - (getTime() - o.disparo) * o.velocidade;
                if (restante < -40) {
                    it.remove();
                    continue;
                }
                if (restante < 0) {
                    registrarPassagem(o);
                    continue;
                }
                double chegada = restante / o.velocidade;
                if (chegada < chegada1) {
                    segunda = primeira;
                    chegada2 = chegada1;
                    primeira = o;
                    chegada1 = chegada;
                } else if (chegada < chegada2) {
                    segunda = o;
                    chegada2 = chegada;
                }
            }
            if (primeira == null) return false;
            int escolha = sentido;
            double custo = avaliar(primeira, segunda, sentido);
            double oposto = avaliar(primeira, segunda, -sentido);
            if (oposto < custo * 0.97) {
                escolha = -sentido;
                custo = oposto;
            }
            if (Math.hypot(getX() - primeira.x, getY() - primeira.y) > 220
                    && avaliar(primeira, segunda, 0) < custo * 0.9) escolha = 0;
            if (escolha != 0) sentido = escolha;
            double curso = cursoOrbital(getX(), getY(), primeira, sentido);
            double giro = Utils.normalRelativeAngle(curso - getHeadingRadians());
            int marcha = Math.cos(giro) < 0 ? -1 : 1;
            if (marcha < 0) giro = Utils.normalRelativeAngle(giro + Math.PI);
            setMaxVelocity(escolha == 0 ? 0 : 8);
            setTurnRightRadians(giro);
            setAhead(marcha * 180);
            return true;
        }

        /* Rumo de orbita em volta do ponto de disparo: perpendicular a ele,
           fechando ou abrindo o angulo para ficar perto de 390 px. Se o rumo
           levaria para a parede, gira aos poucos ate achar um rumo livre. */
        // Quando a onda passa por nos, anota em que ponto dela estavamos.
        void registrarPassagem(OndaInimiga o) {
            if (o.passagemRegistrada) return;
            o.passagemRegistrada = true;
            double fator = Utils.normalRelativeAngle(Math.atan2(getX() - o.x, getY() - o.y)
                    - o.referencia) / o.escape * o.lado;
            double centro = (limitar(fator, -1, 1) + 1) * (BINS_DEFESA - 1) / 2;
            for (int i = 0; i < BINS_DEFESA; i++) {
                double desvio = (i - centro) / 2;
                o.perfil.passagens[i] = 0.95 * o.perfil.passagens[i] + Math.exp(-desvio * desvio / 2);
            }
            o.perfil.ondasPassadas++;
        }

        double cursoOrbital(double x, double y, OndaInimiga o, int lado) {
            double distancia = Math.hypot(x - o.x, y - o.y);
            double rumo =
                    Math.atan2(o.x - x, o.y - y)
                            + lado * (Math.PI / 2 + limitar((390 - distancia) / 350, -0.25, 0.5));
            // Continuidade ao longo da parede, sem mandar toda fuga para o centro.
            for (int i = 0; i < 54; i++) {
                double a = rumo + lado * i * 0.12,
                        px = x + 120 * Math.sin(a),
                        py = y + 120 * Math.cos(a);
                if (px > 25
                        && py > 25
                        && px < getBattleFieldWidth() - 25
                        && py < getBattleFieldHeight() - 25
                        && longeDoColega(px, py)) return a;
            }
            return Math.atan2(getBattleFieldWidth() / 2 - x, getBattleFieldHeight() / 2 - y);
        }

        /* Simula tick a tick onde estaremos seguindo uma opcao (1 ou -1 = orbitar
           para um lado, 0 = frear), com as regras de fisica do Robocode: acelera
           1 por tick, freia 2, velocidade maxima 8 e giro menor quanto mais
           rapido. Quando cada onda nos alcanca, soma o perigo daquele ponto; a
           segunda onda pesa 40%. Bater na parede dentro da simulacao custa muito.
           No fim somamos um pequeno custo por terminar perto da parede. */
        double avaliar(OndaInimiga primeira, OndaInimiga segunda, int opcao) {
            double x = getX(), y = getY(), h = getHeadingRadians(), v = getVelocity(), custo = 0;
            boolean passou1 = false, passou2 = segunda == null;
            for (int t = 1; t <= 95; t++) {
                double giro =
                        Utils.normalRelativeAngle(
                                cursoOrbital(x, y, primeira, opcao == 0 ? sentido : opcao) - h);
                int marcha = Math.cos(giro) < 0 ? -1 : 1;
                if (marcha < 0) giro = Utils.normalRelativeAngle(giro + Math.PI);
                h += limitar(giro, -Rules.getTurnRateRadians(v), Rules.getTurnRateRadians(v));
                // Fisica do Robocode: acelerar +1, frear -2, maximo 8.
                if (opcao == 0) v -= Math.copySign(Math.min(2, Math.abs(v)), v);
                else if (v * marcha >= 0) v = marcha * Math.min(8, Math.abs(v) + 1);
                else if (Math.abs(v) >= 2) v += 2 * marcha;
                else v = marcha * (1 - Math.abs(v) / 2);
                x += v * Math.sin(h);
                y += v * Math.cos(h);
                if (x < 18
                        || y < 18
                        || x > getBattleFieldWidth() - 18
                        || y > getBattleFieldHeight() - 18) return custo + 10;
                if (!passou1
                        && (getTime() + t - primeira.disparo) * primeira.velocidade
                                >= Math.hypot(x - primeira.x, y - primeira.y) - 18) {
                    custo += perigo(primeira, x, y);
                    passou1 = true;
                }
                if (!passou2
                        && (getTime() + t - segunda.disparo) * segunda.velocidade
                                >= Math.hypot(x - segunda.x, y - segunda.y) - 18) {
                    custo += 0.4 * perigo(segunda, x, y);
                    passou2 = true;
                }
                if (passou1 && passou2) break;
            }
            double parede =
                    Math.min(
                            Math.min(x, getBattleFieldWidth() - x),
                            Math.min(y, getBattleFieldHeight() - y));
            return custo + 0.015 * 60 / Math.max(18, parede);
        }

        /* Perigo de sermos atingidos num ponto. Convertemos o ponto em fator de
           desvio (-1 a +1) e somamos o perfil aprendido nas faixas vizinhas, com
           peso proporcional ao tamanho do nosso robo visto dali. Antes de levarmos
           tiros, supomos que o inimigo mira direto (fator 0); esse palpite perde
           peso a cada impacto registrado. */
        double perigo(OndaInimiga o, double x, double y) {
            double fator =
                    Utils.normalRelativeAngle(Math.atan2(x - o.x, y - o.y) - o.referencia)
                            / o.escape
                            * o.lado;
            double centro = (limitar(fator, -1, 1) + 1) * (BINS_DEFESA - 1) / 2;
            double largura =
                    Math.max(
                            1,
                            Math.atan2(24, Math.hypot(x - o.x, y - o.y))
                                    / o.escape
                                    * (BINS_DEFESA - 1)
                                    / 2);
            // O perfil detalhado ganha peso conforme junta impactos no seu grupo.
            int nDetalhe = o.perfil.impactosDetalhe[o.detalhe];
            double pesoDetalhe = 2.0 * nDetalhe / (nDetalhe + 2.0);
            // Taxa de acerto do inimigo contra nos: acima de ~10% ele esta
            // aprendendo nosso padrao, e passamos a evitar nossos lugares habituais.
            double taxa = o.perfil.impactos / (o.perfil.ondasPassadas + 5.0);
            double pesoPassagem = o.perfil.ondasPassadas < 15 ? 0 : limitar((taxa - 0.09) * 6, 0, 0.6);
            double normaPassagem = 1.0 / Math.max(1, o.perfil.ondasPassadas / 12.0);
            double soma = 0, pesos = 0;
            for (int i = 0; i < BINS_DEFESA; i++) {
                double delta = (i - centro) / largura, peso = Math.exp(-delta * delta / 2);
                soma += peso * (o.perfil.perigo[o.segmento][i]
                        + pesoDetalhe * o.perfil.detalhado[o.detalhe][i]
                        + pesoPassagem * normaPassagem * o.perfil.passagens[i]);
                pesos += peso;
            }
            // Antes dos primeiros impactos, fugir da mira direta e plausivel.
            double prior = 0.35 / (1 + o.perfil.impactos / 4.0) * Math.exp(-fator * fator / 0.07);
            return prior + soma / Math.max(0.001, pesos) + 0.025;
        }
    }

    // Todo tick: guarda nossa posicao para o modulo de defesa.
    public void onStatus(StatusEvent e) {
        defesa.guardar(e.getStatus());
    }

    // Nossa bala e uma bala inimiga se chocaram: a onda dessa bala acabou.
    public void onBulletHitBullet(BulletHitBulletEvent e) {
        defesa.impacto(e.getHitBullet(), false);
    }

    // =====================================================================
    // MIRA CONTEXTUAL (busca pelas situacoes mais parecidas)
    // Inspiracao: conceito de "Dynamic Clustering" / KNN da comunidade
    // Robocode. O codigo foi escrito pela equipe. A cada tiro guardamos a
    // situacao do alvo e, quando a bala chega, para onde ele realmente foi. Na
    // hora de atirar buscamos as situacoes guardadas mais parecidas com a
    // atual e miramos no desvio que mais se repetiu entre elas.
    // =====================================================================
    // AMOSTRAS_MIRA: maximo de exemplos guardados por adversario.
    // VIZINHOS_MIRA: quantos exemplos parecidos usamos em cada tiro.
    private static final int AMOSTRAS_MIRA = 240, VIZINHOS_MIRA = 18;
    // Memoria extra alimentada por tiros virtuais (um por tick), bem maior.
    private static final int AMOSTRAS_VIRTUAIS = 1500;
    // Historico por adversario, static para durar entre os rounds; zerado no round 0.
    private static final Map<String, MemoriaContextual> contextos =
            new HashMap<String, MemoriaContextual>();
    private final MiraContextual miraContextual = new MiraContextual();

    // Um exemplo: os atributos da situacao e o fator de desvio real (-1 a +1).
    private static class AmostraContextual {
        double[] atributos;
        double fator;
    }

    // Exemplos de um adversario num buffer circular (o mais velho e
    // sobrescrito) e a nota de cada mira: a base e a contextual.
    private static class MemoriaContextual {
        final AmostraContextual[] amostras = new AmostraContextual[AMOSTRAS_MIRA];
        int total, cursor;
        double notaBase = 0.3, notaContexto = 0.3;
        // Exemplos de todos os tiros, reais e virtuais. Contra robos que nao
        // reagem aos nossos tiros, isso da ~10x mais dados. Contra quem desvia
        // de balas reais, a memoria so de tiros reais (acima) costuma ser melhor;
        // as notas decidem qual usar.
        final AmostraContextual[] virtuais = new AmostraContextual[AMOSTRAS_VIRTUAIS];
        int totalVirtuais, cursorVirtuais;
        double notaVirtual = 0.3;
    }

    // Estado atual de um alvo para esta mira e os angulos do ultimo tiro.
    private static class AlvoContextual {
        String nome;
        double x, y, h, v, giro, base, proposta, propostaVirtual, ultimaPotencia = 1.9;
        long tick = -1, mudouVelocidade;
        MemoriaContextual memoria;
    }

    // Um tiro nosso em voo. Guardamos a situacao no disparo para que, quando
    // a bala chegar, ela vire um novo exemplo.
    private static class VooAprendizado {
        AlvoContextual alvo;
        double x, y, velocidade, referencia, escape, base, proposta, propostaVirtual;
        int lado;
        long tick;
        boolean propostaValida, virtual;
        double[] atributos;
    }

    /* Guardamos exemplos em vez de tabelas com centenas de segmentos vazios,
       o que aprende mais rapido nos poucos rounds do campeonato. A mira base
       continua disputando: usamos a que vem errando menos contra cada alvo. */
    private class MiraContextual {
        final Map<String, AlvoContextual> alvos = new HashMap<String, AlvoContextual>();
        final List<VooAprendizado> voos = new ArrayList<VooAprendizado>();

        /* Atualiza o alvo a cada scan e confere nossos tiros em voo. Quando a onda
           de um tiro passa pelo alvo, o desvio real vira um novo exemplo e as
           duas miras ganham nota conforme o erro de cada uma. So aprendemos com
           scans recentes (ate 2 ticks), senao a posicao seria imprecisa. */
        void observar(ScannedRobotEvent e) {
            AlvoContextual a = alvos.get(e.getName());
            if (a == null) {
                a = new AlvoContextual();
                a.nome = e.getName();
                alvos.put(a.nome, a);
                a.memoria = contextos.get(a.nome);
                if (a.memoria == null) {
                    a.memoria = new MemoriaContextual();
                    contextos.put(a.nome, a.memoria);
                }
            }
            long intervalo = getTime() - a.tick;
            if (Math.abs(e.getVelocity() - a.v) > 0.5) a.mudouVelocidade = getTime();
            a.giro =
                    a.tick >= 0 && intervalo > 0
                            ? Utils.normalRelativeAngle(e.getHeadingRadians() - a.h) / intervalo
                            : 0;
            double direcao = getHeadingRadians() + e.getBearingRadians();
            a.x = getX() + e.getDistance() * Math.sin(direcao);
            a.y = getY() + e.getDistance() * Math.cos(direcao);
            a.h = e.getHeadingRadians();
            a.v = e.getVelocity();
            a.tick = getTime();
            for (Iterator<VooAprendizado> it = voos.iterator(); it.hasNext(); ) {
                VooAprendizado t = it.next();
                if (getTime() - t.tick > 110 || !alvos.containsKey(t.alvo.nome)) {
                    it.remove();
                    continue;
                }
                if (t.alvo != a) continue;
                double distancia = Math.hypot(a.x - t.x, a.y - t.y),
                        raio = (getTime() - t.tick) * t.velocidade;
                if (raio < distancia) continue;
                if (raio - distancia < t.velocidade * 2 && intervalo <= 2) {
                    double angulo = Math.atan2(a.x - t.x, a.y - t.y),
                            largura = Math.atan2(20, distancia);
                    if (t.propostaValida && !t.virtual) {
                        double erroBase = Utils.normalRelativeAngle(angulo - t.base) / largura;
                        double erroNovo = Utils.normalRelativeAngle(angulo - t.proposta) / largura;
                        double erroVirtual =
                                Utils.normalRelativeAngle(angulo - t.propostaVirtual) / largura;
                        a.memoria.notaVirtual =
                                0.9 * a.memoria.notaVirtual
                                        + 0.1 * Math.exp(-erroVirtual * erroVirtual / 2);
                        a.memoria.notaBase =
                                0.9 * a.memoria.notaBase + 0.1 * Math.exp(-erroBase * erroBase / 2);
                        a.memoria.notaContexto =
                                0.9 * a.memoria.notaContexto
                                        + 0.1 * Math.exp(-erroNovo * erroNovo / 2);
                    }
                    AmostraContextual registro = new AmostraContextual();
                    registro.atributos = t.atributos;
                    registro.fator =
                            limitar(
                                    Utils.normalRelativeAngle(angulo - t.referencia)
                                            / t.escape
                                            * t.lado,
                                    -1,
                                    1);
                    // Todo exemplo vai para a memoria grande; so os de tiros reais
                    // vao para a memoria pequena.
                    MemoriaContextual m = a.memoria;
                    m.virtuais[m.cursorVirtuais] = registro;
                    m.cursorVirtuais = (m.cursorVirtuais + 1) % AMOSTRAS_VIRTUAIS;
                    m.totalVirtuais = Math.min(AMOSTRAS_VIRTUAIS, m.totalVirtuais + 1);
                    if (!t.virtual) {
                        m.amostras[m.cursor] = registro;
                        m.cursor = (m.cursor + 1) % AMOSTRAS_MIRA;
                        m.total = Math.min(AMOSTRAS_MIRA, m.total + 1);
                    }
                }
                it.remove();
            }
            // Um tiro virtual por scan: finge que atiramos agora, so para aprender.
            criarVoo(a, a.ultimaPotencia, true);
        }

        /* Descreve a situacao do alvo em 6 numeros de mesma escala: distancia,
           velocidade lateral, velocidade de aproximacao, distancia da parede,
           giro no sentido do movimento e tempo desde a ultima mudanca de
           velocidade. */
        double[] atributos(AlvoContextual a) {
            double direcao = Math.atan2(a.x - getX(), a.y - getY()),
                    lateral = a.v * Math.sin(a.h - direcao);
            double parede =
                    Math.min(
                            Math.min(a.x, getBattleFieldWidth() - a.x),
                            Math.min(a.y, getBattleFieldHeight() - a.y));
            return new double[] {
                Math.hypot(a.x - getX(), a.y - getY()) / 800,
                Math.abs(lateral) / 8,
                a.v * Math.cos(a.h - direcao) / 8,
                Math.min(1, parede / 180),
                limitar(a.giro * (lateral < 0 ? -1 : 1) / 0.12, -1, 1),
                Math.min(1, (getTime() - a.mudouVelocidade) / 35.0)
            };
        }

        /* Escolhe o angulo de tiro entre tres miras: a base, a contextual com
           exemplos so de tiros reais (boa contra quem desvia das nossas balas) e
           a contextual com exemplos de tiros virtuais (muito mais dados, boa
           contra quem nao reage aos tiros). Cada uma recebe nota conforme erra ou
           acerta os tiros reais, e usamos a de melhor nota contra este alvo. */
        double escolher(String nome, double potencia, double base) {
            AlvoContextual a = alvos.get(nome);
            if (a == null) return base;
            a.base = base;
            a.proposta = base;
            a.propostaVirtual = base;
            double escape = Math.asin(8 / Rules.getBulletSpeed(potencia));
            double direcao = Math.atan2(a.x - getX(), a.y - getY());
            int lado = a.v * Math.sin(a.h - direcao) < 0 ? -1 : 1;
            double[] atual = atributos(a);
            MemoriaContextual m = a.memoria;
            if (m.total >= 7)
                a.proposta = direcao + lado * escape * melhorFator(m.amostras, m.total, atual, a, escape);
            if (m.totalVirtuais >= 20)
                a.propostaVirtual =
                        direcao + lado * escape * melhorFator(m.virtuais, m.totalVirtuais, atual, a, escape);
            // Usa a mira com melhor nota contra este alvo.
            double melhor = m.notaBase;
            double escolhido = base;
            if (m.total >= 7 && m.notaContexto >= melhor) {
                melhor = m.notaContexto;
                escolhido = a.proposta;
            }
            if (m.totalVirtuais >= 20 && m.notaVirtual > melhor) escolhido = a.propostaVirtual;
            return escolhido;
        }

        /* Acha os exemplos mais parecidos com a situacao atual (velocidade
           lateral pesa 3x e parede 1.5x), testa 41 fatores de desvio e devolve o
           de maior densidade, dando mais peso aos exemplos mais parecidos. */
        double melhorFator(AmostraContextual[] amostras, int total, double[] atual,
                AlvoContextual a, double escape) {
            double[] distancias = new double[VIZINHOS_MIRA];
            AmostraContextual[] proximas = new AmostraContextual[VIZINHOS_MIRA];
            Arrays.fill(distancias, Double.POSITIVE_INFINITY);
            int quantidade = Math.min(VIZINHOS_MIRA, total);
            for (int i = 0; i < total; i++) {
                AmostraContextual exemplo = amostras[i];
                double distancia = 0;
                for (int k = 0; k < atual.length; k++) {
                    double d = atual[k] - exemplo.atributos[k];
                    distancia += d * d * (k == 1 ? 3 : k == 3 ? 1.5 : 1);
                }
                int pior = 0;
                for (int j = 1; j < quantidade; j++) if (distancias[j] > distancias[pior]) pior = j;
                if (distancia < distancias[pior]) {
                    distancias[pior] = distancia;
                    proximas[pior] = exemplo;
                }
            }
            double largura =
                    Math.max(0.07, Math.atan2(22, Math.hypot(a.x - getX(), a.y - getY())) / escape);
            double melhor = -1, fatorEscolhido = 0;
            for (int bin = 0; bin <= 40; bin++) {
                double fator = bin / 20.0 - 1, densidade = 0;
                for (int j = 0; j < quantidade; j++) {
                    double erro = (fator - proximas[j].fator) / largura;
                    densidade += Math.exp(-erro * erro / 2) / (0.08 + distancias[j]);
                }
                if (densidade > melhor) {
                    melhor = densidade;
                    fatorEscolhido = fator;
                }
            }
            return fatorEscolhido;
        }

        // Registra um tiro real que acabou de sair, para aprender quando ele chegar.
        void registrar(String nome, double potencia) {
            AlvoContextual a = alvos.get(nome);
            if (a == null) return;
            a.ultimaPotencia = potencia;
            criarVoo(a, potencia, false);
        }

        // Cria um voo de aprendizado (real ou virtual) saindo da nossa posicao.
        void criarVoo(AlvoContextual a, double potencia, boolean virtual) {
            VooAprendizado t = new VooAprendizado();
            t.alvo = a;
            t.virtual = virtual;
            t.x = getX();
            t.y = getY();
            t.tick = getTime();
            t.velocidade = Rules.getBulletSpeed(potencia);
            t.escape = Math.asin(8 / t.velocidade);
            t.referencia = Math.atan2(a.x - t.x, a.y - t.y);
            t.lado = a.v * Math.sin(a.h - t.referencia) < 0 ? -1 : 1;
            t.base = a.base;
            t.proposta = a.proposta;
            t.propostaVirtual = a.propostaVirtual;
            t.propostaValida = a.memoria.total >= 7;
            t.atributos = atributos(a);
            if (voos.size() >= 200) voos.remove(0);
            voos.add(t);
        }

        // O alvo morreu: paramos de acompanha-lo.
        void remover(String nome) {
            alvos.remove(nome);
        }
    }
}
