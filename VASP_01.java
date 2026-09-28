/*
Equipe: VASP
Integrantes:
- Yan Maciel Fonseca Suzano
- Pedro de Sousa Monteiro Barbosa
- Nicoly Stefany Moura Ferreira
- Sophia Bernardo de Oliveira

ROBO 01: ORBITA E MIRA COMBINADA

Papel na dupla: fora do duelo ele orbita o alvo e escolhe entre varias miras.
Nos nossos testes em 2x2 contra duplas de campeoes da comunidade, esse estilo
rendeu mais contra robos feitos para melee (varios inimigos), enquanto o robo 02
rendeu mais contra robos feitos para duelo. No 1v1 os dois usam a mesma defesa
por ondas e a mesma mira contextual.

RADAR
O radar e independente do corpo e do canhao, por isso os tres setAdjust no
run. Em duelo, com o alvo visto ha pouco tempo, giramos o radar direto para
ele e passamos um pouco do ponto exato. Esse "passar um pouco" e de proposito,
porque se apontassemos para o angulo exato o alvo se deslocaria no tick
seguinte e sairia do feixe, perdendo a trava. No melee, ou com o alvo visto ha
muito tempo, o radar gira sem parar, porque ai o importante e enxergar todo
mundo.

MOVIMENTACAO
Com ate 2 inimigos vivos (duelo, ou 2x2 com o colega) estimamos disparos pela
queda de energia, descontando danos conhecidos. Com 2 inimigos o radar vai e
volta entre eles para rever cada um a cada poucos ticks.
Com ondas confiaveis, simulamos continuar, inverter e frear ate a passagem das
duas ondas mais proximas. Os impactos reais atualizam o mapa de perigo de cada
adversario. A previsao respeita velocidade, aceleracao, frenagem e giro limitado.
Sem onda confiavel, usamos a orbita com inversoes sorteadas. Com 3 ou mais
inimigos mantemos essa orbita e a recuperacao de parede, pois quedas de energia
de muitos atiradores nao permitem identificar um disparo com a mesma confianca.

MIRA
A base compara quatro modelos: direto, linear, circular e histograma GuessFactor.
Uma segunda consulta procura ate 18 situacoes semelhantes num historico limitado
a 240 exemplos por adversario. Considera distancia, velocidade lateral, avancar
ou recuar, parede, giro e tempo sem mudar velocidade. Os desvios mais frequentes
entre esses vizinhos geram uma proposta de tiro.
A proposta contextual disputa com o resultado da mira base. Avaliamos as duas
na mesma passagem de onda, mesmo quando apenas uma delas foi usada para atirar.
Com pouco historico, a base continua responsavel pela mira. Tudo reinicia no
primeiro round da batalha; estatisticas pequenas persistem entre os cinco rounds.

CONTROLE DE POTENCIA
Nao atiramos sempre com forca 3. A potencia sobe quando o alvo esta perto,
porque e mais facil acertar, cai quando ele esta longe e cai muito se a nossa
energia estiver baixa. Ela tambem nunca passa do necessario para finalizar um
inimigo quase morto. No Robocode atirar custa energia, entao bala desperdicada
e vida perdida.

DIFERENCA 1v1 / MELEE
O radar trava no duelo, alterna entre os dois inimigos quando sao 2 e varre no
melee maior. A defesa por ondas e usada com ate 2 inimigos; com mais, vale a
orbita.
A selecao de alvo compara distancia, energia e idade da observacao.

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
- GuessFactor: mira por histograma do desvio lateral do alvo.
- Virtual Guns: avaliar varias miras ao mesmo tempo e usar a que acerta mais.
- Wave Surfing: desviar de balas tratando cada disparo como uma onda.
- Dynamic Clustering / KNN: mirar pelas situacoes mais parecidas do historico.
- Tick waves (tiros virtuais): aprender a cada tick, e nao so a cada tiro real.
- Flattener (achatamento): quando o inimigo acerta muito, evitar tambem os
  lugares onde costumamos estar, para nao deixar padrao para ele aprender.
*/
import robocode.*;
import robocode.util.Utils;
import java.util.*;

public class VASP_01 extends TeamRobot {
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

    // Parametros da base; limites das novas estruturas ficam junto aos modulos.
    private static final double MARGEM_PAREDE = 42, DISTANCIA_PROJECAO = 115, DISTANCIA_IDEAL_DUELO = 350;
    private static final int LIMITE_SCAN_ANTIGO = 24; // depois disso a leitura do inimigo e velha demais
    private static final int FAIXAS_MIRA = 25, MAX_TIROS_ACOMPANHADOS = 48;

    /* Estatisticas de mira por nome de adversario. E static de proposito. No
       Robocode o robo e recriado a cada round, e testamos que campos static
       sobrevivem entre os rounds da mesma batalha, entao o que aprendemos no
       round 1 continua valendo no round 2. Mesmo assim o robo funciona bem sem
       nenhum historico, porque a nota inicial ja aponta para a mira linear. */
    private static final Map<String, MemoriaMira> memoriaAprendizado = new HashMap<String, MemoriaMira>();

    private final Map<String, EstadoInimigo> inimigos = new LinkedHashMap<String, EstadoInimigo>();
    private final List<RegistroTiroVirtual> tirosVirtuais = new ArrayList<RegistroTiroVirtual>();
    private EstadoInimigo alvoAtual;
    private int sentidoOrbita = 1; // 1 ou -1: sentido da orbita
    private long proximaInversao = 35, ultimaColisao = -100;

    // Tudo o que sabemos de um adversario no momento do ultimo scan.
    private static class EstadoInimigo {
        String nome;
        double x, y, direcao, velocidade, taxaGiro, energia, distancia;
        long instanteScan = -1; // quando foi visto pela ultima vez
        MemoriaMira memoria;
    }

    // Memoria de mira de um adversario, com as notas dos modelos e o histograma de desvios.
    private static class MemoriaMira {
        // Uma linha de histograma para cada faixa de velocidade lateral do alvo.
        // Usamos so tres faixas porque com 5 rounds nao da tempo de encher mais.
        double[][] histogramaDesvio = new double[3][FAIXAS_MIRA];
        // As notas comecam confiando mais na mira linear, que e a que funciona
        // melhor "no escuro", antes de termos qualquer observacao.
        double[] notaModelos = { 0.18, 0.32, 0.28, 0.18 };
        int observacoes;
    }

    // Um tiro ja disparado que ainda estamos acompanhando para aprender com ele.
    private static class RegistroTiroVirtual {
        String nome;
        double x, y, velocidadeBala, anguloDireto, anguloEscape;
        double[] angulosPrevistos; // os quatro angulos previstos naquele tiro
        int lado, segmento;
        long instanteTiro;
    }

    public void run() {
        String[] colegas = getTeammates();
        colegaVivo = colegas != null && colegas.length > 0;
        // No comeco da batalha (round 0) zeramos tudo o que foi aprendido, para
        // uma batalha nao contaminar a seguinte. Nos rounds 1 a 4 o aprendizado
        // continua valendo.
        if (getRoundNum() == 0) {
            perfisDefesa.clear();
            contextos.clear();
            memoriaAprendizado.clear();
        }

        // Radar, canhao e corpo giram de forma independente. Sem isso, virar o
        // corpo arrastaria o canhao junto e estragaria a mira.
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);
        setColors(new java.awt.Color(30, 90, 180), java.awt.Color.WHITE, java.awt.Color.CYAN);

        while (true) {
            escolherAlvo();
            controlarRadar();
            controlarMovimento();
            // So atiramos com informacao fresca. Mirar em posicao velha erra feio.
            if (alvoAtual != null && getTime() - alvoAtual.instanteScan <= 2)
                mirarEAtirar(alvoAtual);
            execute();
        }
    }

    // Atualiza o que sabemos de quem o radar acabou de ver.
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
        EstadoInimigo c = inimigos.get(event.getName());
        if (c == null) {
            c = new EstadoInimigo();
            c.nome = event.getName();
            inimigos.put(c.nome, c);
            c.memoria = memoriaAprendizado.get(c.nome);
            if (c.memoria == null) {
                c.memoria = new MemoriaMira();
                memoriaAprendizado.put(c.nome, c.memoria);
            }
        }

        // Quanto o inimigo girou desde o ultimo scan, dividido pelos ticks que
        // passaram. E isso que alimenta a mira circular.
        long ticksDecorridos = getTime() - c.instanteScan;
        c.taxaGiro = c.instanteScan >= 0 && ticksDecorridos > 0
                ? Utils.normalRelativeAngle(event.getHeadingRadians() - c.direcao) / ticksDecorridos
                : 0;

        /* O evento so informa direcao e distancia relativas, entao convertemos
           para coordenadas absolutas do campo. No Robocode o angulo e medido a
           partir do norte e no sentido horario, por isso x usa sin e y usa cos. */
        double anguloAbsoluto = getHeadingRadians() + event.getBearingRadians();
        c.x = getX() + event.getDistance() * Math.sin(anguloAbsoluto);
        c.y = getY() + event.getDistance() * Math.cos(anguloAbsoluto);

        c.direcao = event.getHeadingRadians();
        c.velocidade = event.getVelocity();
        c.energia = event.getEnergy();
        c.distancia = event.getDistance();
        c.instanteScan = getTime();
        atualizarAprendizado(c);
    }

    /* Escolhe em quem atirar. No duelo so existe um candidato, entao e no melee
       que essa funcao importa. Preferimos quem esta perto, quem foi visto ha
       pouco e quem tem pouca energia, porque esta mais perto de morrer e
       finalizar vale ponto. */
    private void escolherAlvo() {
        EstadoInimigo melhor = null;
        double menorCusto = Double.POSITIVE_INFINITY;
        for (EstadoInimigo c : inimigos.values()) {
            c.distancia = Math.hypot(c.x - getX(), c.y - getY());
            double custo = c.distancia + 5 * (getTime() - c.instanteScan) + 1.2 * c.energia;
            // Quem ja e o alvo atual ganha um desconto, para nao ficarmos trocando
            // de alvo toda hora sem nunca terminar de girar o canhao.
            if (c == alvoAtual)
                custo *= 0.88;
            if (getTime() - c.instanteScan <= LIMITE_SCAN_ANTIGO && custo < menorCusto) {
                melhor = c;
                menorCusto = custo;
            }
        }
        alvoAtual = melhor;
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

    private void controlarRadar() {
        if (radarNoColega())
            return;
        // No melee, sem alvo ou com alvo velho, o radar gira sem parar para mapear o campo.
        if (adversariosVivos() == 2 && radarEntreDois())
            return;
        if (adversariosVivos() != 1 || alvoAtual == null
                || getTime() - alvoAtual.instanteScan > 3) {
            setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
            return;
        }
        /* No duelo apontamos para o alvo e passamos um pouco do ponto exato. A
           margem extra cresce quando o alvo esta perto, porque de perto ele ocupa
           mais angulo e se desloca mais rapido em relacao ao feixe. */
        double diferenca = Utils.normalRelativeAngle(anguloAte(alvoAtual.x, alvoAtual.y) - getRadarHeadingRadians());
        setTurnRadarRightRadians(diferenca + Math.copySign(0.035 + Math.atan2(28, alvoAtual.distancia), diferenca));
    }

    /* Com dois inimigos, apontamos o radar para o que esta ha mais tempo sem
       ser visto, passando um pouco do ponto. Assim o radar vai e volta entre
       os dois e cada um e revisto a cada poucos ticks, o que permite detectar
       os disparos deles. Retorna falso se ainda nao conhecemos os dois. */
    private boolean radarEntreDois() {
        EstadoInimigo maisAntigo = null;
        int conhecidos = 0;
        for (EstadoInimigo c : inimigos.values()) {
            if (getTime() - c.instanteScan > 12)
                continue;
            conhecidos++;
            if (maisAntigo == null || c.instanteScan < maisAntigo.instanteScan)
                maisAntigo = c;
        }
        if (conhecidos < 2)
            return false;
        double diferenca = Utils.normalRelativeAngle(anguloAte(maisAntigo.x, maisAntigo.y) - getRadarHeadingRadians());
        double folga = Math.atan2(30 + 8 * (getTime() - maisAntigo.instanteScan), Math.max(40, maisAntigo.distancia));
        setTurnRadarRightRadians(diferenca + Math.copySign(folga, diferenca));
        return true;
    }

    private void controlarMovimento() {
        // Com ate 2 inimigos, se ha onda de disparo a caminho, o modulo de defesa decide
        // o movimento neste tick.
        if (defesa.mover())
            return;
        /* Inverte o sentido da orbita em intervalos sorteados. Se o intervalo
           fosse fixo, nosso movimento viraria uma onda previsivel e qualquer
           adversario com estatistica acertaria facil. */
        if (getTime() >= proximaInversao) {
            sentidoOrbita = -sentidoOrbita;
            proximaInversao = getTime() + 30 + Utils.getRandom().nextInt(35);
        }

        /* Encostados na parede perdemos a orbita e viramos alvo parado, entao
           voltamos ao campo primeiro. Sem essa verificacao o robo pode ficar
           prensado no canto, principalmente com um adversario colado. */
        if (!dentroMargemSegura(getX(), getY())) {
            seguirRumo(anguloAte(getBattleFieldWidth() / 2, getBattleFieldHeight() / 2));
            return;
        }

        /* Rumo perpendicular ao alvo, que e o +PI/2, mais uma correcao de
           distancia. Se estamos mais longe que o ideal, fechamos um pouco o angulo
           para aproximar, e se estamos perto demais, abrimos para afastar. */
        double rumo = alvoAtual == null ? anguloAte(getBattleFieldWidth() / 2, getBattleFieldHeight() / 2)
                : anguloAte(alvoAtual.x, alvoAtual.y) + sentidoOrbita
                        * (Math.PI / 2 + limitar((DISTANCIA_IDEAL_DUELO - alvoAtual.distancia) / 300, -0.4, 0.6));
        seguirRumo(calcularRumoSeguro(rumo, sentidoOrbita));
    }

    // Verdadeiro se o ponto esta dentro do campo com folga para a parede.
    private boolean dentroMargemSegura(double x, double y) {
        return x > MARGEM_PAREDE && y > MARGEM_PAREDE
                && x < getBattleFieldWidth() - MARGEM_PAREDE && y < getBattleFieldHeight() - MARGEM_PAREDE;
    }

    /* Desvio de parede. Projeta onde estariamos daqui a pouco seguindo o rumo
       desejado e, se cair fora da area segura, vai abrindo o angulo aos poucos
       no sentido da orbita ate achar um rumo que sirva. Abrimos no sentido da
       orbita para continuar contornando o inimigo em vez de dar meia-volta. */
    private double calcularRumoSeguro(double rumoDesejado, int sentido) {
        // A busca e limitada, e se nenhuma projecao servir voltamos ao centro. O
        // laco tem tamanho fixo de proposito, para nunca custar tempo demais no tick.
        for (int i = 0; i < 60; i++) {
            double a = rumoDesejado + sentido * i * Math.PI / 30;
            double x = getX() + DISTANCIA_PROJECAO * Math.sin(a), y = getY() + DISTANCIA_PROJECAO * Math.cos(a);
            if (x > MARGEM_PAREDE && y > MARGEM_PAREDE && x < getBattleFieldWidth() - MARGEM_PAREDE
                    && y < getBattleFieldHeight() - MARGEM_PAREDE && longeDoColega(x, y))
                return a;
        }
        return anguloAte(getBattleFieldWidth() / 2, getBattleFieldHeight() / 2);
    }

    /* Converte um rumo absoluto em comandos de virar e andar. Se o rumo estiver
       para tras, andamos de re em vez de girar o corpo inteiro, porque girar
       demora e durante a virada ficamos lentos e faceis de acertar. */
    private void seguirRumo(double rumo) {
        double diferenca = Utils.normalRelativeAngle(rumo - getHeadingRadians());
        int sentidoMarcha = 1;
        if (Math.abs(diferenca) > Math.PI / 2) {
            diferenca = Utils.normalRelativeAngle(diferenca + Math.PI);
            sentidoMarcha = -1;
        }
        setTurnRightRadians(diferenca);
        setAhead(sentidoMarcha * 160);
    }

    /* Calcula os quatro angulos candidatos, gira o canhao para o melhor deles e
       dispara se valer a pena. Cada tiro fica registrado para ser avaliado
       depois, em atualizarAprendizado(). */
    private void mirarEAtirar(EstadoInimigo c) {
        double potencia = calcularPotenciaTiro(c);
        double anguloDireto = anguloAte(c.x, c.y); // angulo direto ate o alvo

        /* Velocidade lateral e o quanto o alvo se desloca "de lado" em relacao a
           nos. E o que interessa para a mira, porque o movimento de aproximacao ou
           afastamento quase nao muda o angulo do tiro. */
        double velocidadeLateral = c.velocidade * Math.sin(c.direcao - anguloDireto);
        int lado = velocidadeLateral < 0 ? -1 : 1, segmento = Math.min(2, (int) (Math.abs(velocidadeLateral) / 3));

        /* Angulo maximo que o alvo consegue "escapar" antes da bala chegar,
           considerando a velocidade maxima de 8 do Robocode. Ele serve de escala
           para o histograma, e assim o histograma vale para qualquer distancia. */
        double anguloEscape = Math.asin(8 / Rules.getBulletSpeed(potencia));

        // Os quatro candidatos: direto, linear, circular e estatistico.
        double[] angulosPrevistos = { anguloDireto, preverPosicao(c, potencia, false), preverPosicao(c, potencia, true),
                0 };

        // O candidato estatistico e o pico do histograma daquele segmento.
        int faixaPico = FAIXAS_MIRA / 2;
        for (int i = 0; i < FAIXAS_MIRA; i++)
            if (c.memoria.histogramaDesvio[segmento][i] > c.memoria.histogramaDesvio[segmento][faixaPico])
                faixaPico = i;
        // Com poucas observacoes o histograma ainda nao significa nada, entao
        // devolvemos o angulo linear em vez de atirar num dado vazio.
        angulosPrevistos[3] = c.memoria.observacoes < 6 ? angulosPrevistos[1]
                : anguloDireto + lado * anguloEscape * (2.0 * faixaPico / (FAIXAS_MIRA - 1) - 1);

        // Escolhe o modelo com a melhor nota contra este adversario.
        int modelo = 1;
        for (int i = 0; i < angulosPrevistos.length; i++)
            if (c.memoria.notaModelos[i] > c.memoria.notaModelos[modelo])
                modelo = i;
        // A mira contextual ainda pode trocar esse angulo, se ela vier acertando mais.
        double anguloEscolhido = miraContextual.escolher(c.nome, potencia, angulosPrevistos[modelo]);

        double diferenca = Utils.normalRelativeAngle(anguloEscolhido - getGunHeadingRadians());
        setTurnGunRightRadians(diferenca);

        // So atira com o canhao frio, ja praticamente alinhado (a tolerancia e a
        // largura angular do alvo) e com energia sobrando.
        if (getGunHeat() == 0 && Math.abs(diferenca) < Math.atan2(16, Math.max(36, c.distancia))
                && getEnergy() > potencia + 0.3 && !colegaNaLinha(anguloEscolhido, c.distancia)) {
            Bullet bala = setFireBullet(potencia);
            if (bala != null) {
                miraContextual.registrar(c.nome, potencia);
                RegistroTiroVirtual t = new RegistroTiroVirtual();
                t.nome = c.nome;
                t.x = getX();
                t.y = getY();
                t.instanteTiro = getTime();
                t.velocidadeBala = Rules.getBulletSpeed(potencia);
                t.anguloDireto = anguloDireto;
                t.anguloEscape = anguloEscape;
                t.lado = lado;
                t.segmento = segmento;
                t.angulosPrevistos = angulosPrevistos;
                // A lista tem tamanho maximo. Se crescesse sem limite, o custo por
                // tick aumentaria e o robo comecaria a perder turnos.
                if (tirosVirtuais.size() >= MAX_TIROS_ACOMPANHADOS)
                    tirosVirtuais.remove(0);
                tirosVirtuais.add(t);
            }
        }
    }

    /* Simula para onde o alvo vai enquanto a bala viaja e devolve o angulo de
       tiro. Com preverCurva=false ele segue reto, que e a mira linear, e com
       preverCurva=true ele continua fazendo a curva que estava fazendo, que e a
       mira circular. Paramos quando a bala ja teria percorrido a distancia ate ele. */
    private double preverPosicao(EstadoInimigo c, double potencia, boolean preverCurva) {
        double x = c.x, y = c.y, velocidadeBala = Rules.getBulletSpeed(potencia);
        double h = c.direcao;
        for (int t = 1; t <= 100; t++) {
            // Limitamos a curva prevista porque, se o alvo deu uma guinada
            // momentanea, sem limite a simulacao o mandaria girando em circulo pelo campo.
            if (preverCurva)
                h += limitar(c.taxaGiro, -0.12, 0.12);
            // O alvo nao sai do campo, entao a simulacao tambem nao sai.
            x = limitar(x + c.velocidade * Math.sin(h), 18, getBattleFieldWidth() - 18);
            y = limitar(y + c.velocidade * Math.cos(h), 18, getBattleFieldHeight() - 18);
            if (t * velocidadeBala >= Math.hypot(x - getX(), y - getY()))
                break;
        }
        return anguloAte(x, y);
    }

    /* Aprendizado. Para cada tiro antigo, quando a bala ja teria alcancado o
       inimigo, comparamos onde ele realmente estava com os quatro angulos que
       tinhamos previsto. Avaliamos os quatro, nao so o que foi usado, e assim
       descobrimos qual modelo seria o melhor mesmo sem ter atirado com ele. */
    private void atualizarAprendizado(EstadoInimigo c) {
        for (Iterator<RegistroTiroVirtual> it = tirosVirtuais.iterator(); it.hasNext();) {
            RegistroTiroVirtual t = it.next();
            // Descarta tiros velhos demais ou de alvos que ja morreram.
            if (getTime() - t.instanteTiro > 110 || !inimigos.containsKey(t.nome)) {
                it.remove();
                continue;
            }
            if (!t.nome.equals(c.nome))
                continue;

            double distancia = Math.hypot(c.x - t.x, c.y - t.y),
                    distanciaPercorrida = (getTime() - t.instanteTiro) * t.velocidadeBala;
            if (distanciaPercorrida < distancia)
                continue; // a bala ainda nao chegou la

            // Scans atrasados nao permitem avaliar a passagem da onda com precisao.
            if (distanciaPercorrida - distancia < t.velocidadeBala * 2) {
                double anguloObservado = Math.atan2(c.x - t.x, c.y - t.y), larguraAlvo = Math.atan2(22, distancia);
                /* A nota e por proximidade, e nao por acerto ou erro. Erro pequeno
                   vale quase 1 e erro grande vale quase 0. Com 5 rounds nao ha
                   amostra para contar acertos, mas ha para medir o quanto erramos. */
                for (int k = 0; k < 4; k++) {
                    double erroNormalizado = Utils.normalRelativeAngle(anguloObservado - t.angulosPrevistos[k])
                            / larguraAlvo;
                    c.memoria.notaModelos[k] = 0.92 * c.memoria.notaModelos[k]
                            + 0.08 * Math.exp(-erroNormalizado * erroNormalizado / 2);
                }

                // Onde o inimigo passou, numa escala de -1 (fugiu para um lado) a
                // +1 (fugiu para o outro), normalizada pelo angulo de escape.
                double fatorDesvio = limitar(
                        Utils.normalRelativeAngle(anguloObservado - t.anguloDireto) / t.anguloEscape * t.lado, -1, 1);
                double faixaAlvo = (fatorDesvio + 1) * (FAIXAS_MIRA - 1) / 2;
                // Somamos uma curva suave em vez de marcar so uma casa, porque uma
                // unica observacao ja indica a regiao, e nao apenas o ponto exato.
                for (int j = 0; j < FAIXAS_MIRA; j++) {
                    double distanciaFaixa = j - faixaAlvo;
                    c.memoria.histogramaDesvio[t.segmento][j] = 0.97 * c.memoria.histogramaDesvio[t.segmento][j]
                            + Math.exp(-distanciaFaixa * distanciaFaixa / 3);
                }
                c.memoria.observacoes++;
            }
            it.remove();
        }
    }

    /* Potencia do tiro. Tres limites valem ao mesmo tempo e usamos o menor deles.
       - Orcamento por distancia, porque de perto vale forca e de longe nao compensa.
       - Quanto basta para finalizar o inimigo: o dano e 4p ate potencia 1 e
         4p + 2(p - 1) acima disso. Por isso usamos energia/4 ate energia 4 e
         (energia + 2)/6 acima, evitando gastar potencia alem do necessario.
       - Uma fracao da nossa propria energia, para nao nos enfraquecermos. */
    private double calcularPotenciaTiro(EstadoInimigo c) {
        double orcamento = getEnergy() < 12 ? 0.7 : c.distancia < 170 ? 2.8 : c.distancia < 420 ? 2.1 : 1.35;
        double paraFinalizar = c.energia <= 4 ? c.energia / 4 : (c.energia + 2) / 6;
        return limitar(Math.min(orcamento, Math.min(paraFinalizar, getEnergy() / 7)), 0.1, 3);
    }

    // Angulo absoluto da nossa posicao ate um ponto do campo.
    private double anguloAte(double x, double y) {
        return Math.atan2(x - getX(), y - getY());
    }

    // Prende um valor entre um minimo e um maximo.
    private static double limitar(double x, double minimo, double maximo) {
        return Math.max(minimo, Math.min(maximo, x));
    }

    // Quando um inimigo morre, tiramos do mapa para nao mirar em fantasma nem
    // deixar a lista crescendo durante a batalha.
    public void onRobotDeath(RobotDeathEvent e) {
        if (mesmaEquipe(e.getName())) {
            colegaVivo = false;
            colegaVisto = -100;
        }
        miraContextual.remover(e.getName());
        inimigos.remove(e.getName());
        if (alvoAtual != null && alvoAtual.nome.equals(e.getName()))
            alvoAtual = null;
    }

    // Bateu na parede: inverte a orbita e segura a proxima inversao por um
    // tempo, para nao ficar oscilando contra a parede.
    public void onHitWall(HitWallEvent e) {
        setMaxVelocity(8);
        sentidoOrbita = -sentidoOrbita;
        proximaInversao = getTime() + 25;
    }

    /* Bateu em outro robo: inverte a orbita para contornar. O intervalo minimo
       evita inverter varias vezes seguidas na mesma colisao, o que travaria o
       robo no lugar. Nao damos comando de movimento aqui de proposito, porque o
       laco principal roda logo depois e sobrescreveria qualquer setAhead/setBack. */
    public void onHitRobot(HitRobotEvent e) {
        setMaxVelocity(8);
        defesa.colisao(e);
        if (getTime() - ultimaColisao > 8) {
            sentidoOrbita = -sentidoOrbita;
            ultimaColisao = getTime();
        }
    }

    // Se um turno for perdido queremos saber, mas nada de imprimir durante a
    // batalha: escrever no console custa tempo e faria perder ainda mais turnos.
    public void onSkippedTurn(SkippedTurnEvent e) {
        // O engine registra o evento no benchmark.
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

    // Eventos repassados ao modulo de defesa, para a deteccao de disparos
    // e o aprendizado de onde fomos atingidos.
    public void onBulletHit(BulletHitEvent e) {
        defesa.nossoAcerto(e);
    }

    public void onHitByBullet(HitByBulletEvent e) {
        defesa.impacto(e.getBullet(), true);
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
