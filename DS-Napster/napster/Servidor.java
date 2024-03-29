package napster;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * Classe principal com o <code>main</code> do servidor, que inicia as threads de apoio e fica em loop esperando por requisições dos peers
 */
public class Servidor {
	/** Socket que recebera as requisicoes udp dos peers */
	static DatagramSocket serverSocket;
	/** Lista que armazenara as informações dos peers que estão conectados e seus respectivos arquivos. A lista possui itens no formato 'ip:udpPorta:tcpPorta,arquivo.extensao' */
	protected static List<String> peerFiles = new ArrayList<>();
	/** Array que é atualizado pela Thread ServerAnswerThread cada vez que uma requisição Alive_OK é recebida e lida pela Thread AliveSender para saber quais peers ainda estão vivos. Contém strings no formato ip:udpPorta */
	protected static ArrayList<String> alivePeers = new ArrayList<String>();
	//* Semaforo para evitar problemas de concorrencia estre as thread */
	protected static Semaphore sem = new Semaphore(1);

	/** Inicializa as threads necessárias e fica em loop esperando requisições */
	public static void main(String args[]) throws Exception{
		//buffer que le info do teclado
		BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
		//Inicializo o socket do server
		serverSocket = new DatagramSocket(10098, InetAddress.getByName(inFromUser.readLine()));

		//Inicializo a thread que verifica se os peers estão vivos
		AliveSender as = new AliveSender();
		as.start();

		//Loop que espera pelo recebimento de mensagens e cria threads para atendimento cada vez que um pacote é recebido
		while(true) {
			byte[] recBuffer = new byte[1024];

			DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);

			serverSocket.receive(recPkt); //BLOCKING

			//Dados do pacote são enviados para a thread para tratamento da requisição
			ServerAnswerThread thread = new ServerAnswerThread(recPkt);
			thread.start();
		}
	}

	/** Função auxiliar com a finalidade de converter uma lista da Classe ArrayList de String para um array de Strings
	 * @param  l   lista de strings implementada como um ArrayList
	 * @return     a lista l convertida para um array de Strings com cada elemento nos mesmo indices. Se a ArrayList recebida for <code>null</code>, então <code>null</code> é retornado.
	 */
	public static String[] arrayLisit2Array(ArrayList<String> l) {
		if (l == null) {
			return null;
		}

		String[] array = new String[l.size()];

		for (int i = 0; i < l.size(); i++) {
			array[i] = l.get(i);
		}
		return array;
	}
}

/** Thread que é instanciada para cada requisição que deve ser tratada pelo servidor. Recebe o pacote com as informações da requisição e envia uma mensagem de resposta adequada. */
class ServerAnswerThread extends Thread{

	/** Pacote que contém uma classe Mensagem.*/
	private DatagramPacket pkt;

	public ServerAnswerThread(DatagramPacket pkt) {
		this.pkt = pkt;
	}

	public void run() {
		try {
			//Uso a função auxiliar na classe Peer para converter os dados do pacote para a classe de mensagem
			Mensagem msg = Peer.BytestoMsg(pkt.getData());

			//Encaminho a requisição para função adequada dependendo do tipo de requisição
			switch (msg.reqtype) {
				case "JOIN":
					joinOk(msg, pkt.getAddress(), pkt.getPort());
					break;
				case "LEAVE":
					leaveOk(pkt.getAddress(), pkt.getPort());
					break;
				case "SEARCH":
					//O campo fileNames da mensagem contem apenas um elemento que e o nome do arquivo desejado
					search(msg.fileNames[0], pkt.getAddress(), pkt.getPort());
					break;
				case "UPDATE":
					//O campo fileNames da mensagem contem apenas um elemento que e o nome do arquivo baixado pelo peer
					update(msg.fileNames[0], pkt.getAddress(), pkt.getPort());
					break;
				case "ALIVE_OK":
					//Substring para remover uma '/' inicial
					//Peer vivo é adicionado na lista do servido como ip:udpPorta
					Servidor.alivePeers.add((pkt.getSocketAddress() + "").substring(1));
					break;
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	/** Função que adiciona os arquivos do peer na lista do servidor e envia para o peer a respota JOIN_OK para informar que ele foi adicionado corretamente
	 * @param  msg        Mensagem com os nomes dos arquivos que associados ao respectivo peer
	 * @param  ipAddres   Ip do peer que enviou a requisicao
	 * @param  port       Porta do peer que enviou a requisicao
	 */
	private void joinOk(Mensagem msg, InetAddress ipAddress, int port) throws IOException, InterruptedException {
		//O primeiro item da lista é a porta tcp para download do peer
		String tcpDownloadPort = msg.fileNames[0];

		//A partir daqui é necessário ter acesso à peerFiles
		Servidor.sem.acquire();

		//Primeiro verifico se o peer ja esta presente na rede
		boolean add = true;
		for (int i = 0; i < Servidor.peerFiles.size(); i++) {
			//E necessario comparar o ip:udpPorta dos dados armazenados com os parametros recebidos
			String[] slices = Servidor.peerFiles.get(i).split(":");
			String iPeer = slices[0] + slices[1];

			String newPeer = ipAddress.getHostAddress() + port;
			if (iPeer.compareTo(newPeer) == 0) {
				add = false;
				break;
			}
		}

		//Array que será usado para o print
		String[] files = new String[msg.fileNames.length - 1];

		//Se o peer não tiver arquivos, apenas guardo um registro que ele está na rede
		if (files.length == 0) {
			Servidor.peerFiles.add(ipAddress.getHostAddress() + ":" + port + ":" + tcpDownloadPort + ",");
		}
		//Armazena os arquivos como ip:udpPorta:tcpPorta,arquivo.extencao
		for (int i = 1; i < msg.fileNames.length; i++) {
			//apenas adiciono os arquivos se o peer não esta na rede
			if (add) {
				Servidor.peerFiles.add(ipAddress.getHostAddress() + ":" + port + ":" + tcpDownloadPort + "," + msg.fileNames[i]);
			}
			
			files[i - 1] = msg.fileNames[i];
		}
		
		//peerFiles ja foi usado
		Servidor.sem.release();

		if (add) {
			System.out.println("Peer " + ipAddress.getHostAddress() + ":" + port + " adicionado com arquivos " + Peer.stringArrayConcat(files));
		}

		//Crio a mensagem de join_ok com os arquivos que foram associados ao peer
		Mensagem answer = new Mensagem("JOIN_OK", files);
		//Uso o metodo do peer que envia mensagens
		Peer.sendMsg(Servidor.serverSocket, answer, ipAddress, port);
	}

	/**
	 * Função que recebe o ip:udpPorta do peer e remove da lista do Servidor os arquivos associados ao mesmo
	 * @param ipAddress   Ip do peer que será eliminado
	 * @param port        Porta udp do peer que será eliminado
	 */
	private void leaveOk(InetAddress ipAddress, int port) throws IOException, InterruptedException {
		//A partir daqui é necessário ter acesso à peerFiles
		Servidor.sem.acquire();

		//Excluo os dados do peer
		for(int i = 0; i < Servidor.peerFiles.size(); i++) {
			//Substring é usado para tirar a / inicial
			if (Servidor.peerFiles.get(i).contains( (pkt.getSocketAddress() + "").substring(1) )) {
				//indice corrigido para não pular elementos
				Servidor.peerFiles.remove(i--);
			}
		}

		//peerFiles ja foi usado
		Servidor.sem.release();
		
		//Crio a mensagem de resposta e uso o método auxiliar no Peer  para enviar
		Mensagem answer = new Mensagem("LEAVE_OK");
		Peer.sendMsg(Servidor.serverSocket, answer, ipAddress, port);
	}

	/**
	 * Função que recebe o ip:udpPorta do peer que enviou a requisição 
	 * e o nome de um arquivo que deve ser procurado na lista do servidor
	 * @param fileName    Nome do arquivo que será procurado
	 * @param ipAddress   Ip do Peer que recebera a resposta
	 * @param port        Porta udp do peer que recebera a resposta
	 */
	private void search(String fileName, InetAddress ipAddress, int port) throws IOException, InterruptedException {
		System.out.println(("Peer " + ipAddress.getHostAddress()+ ":" + port + " soliticou o arquivo " + fileName));

		//Lista que contera os peers que possuem o arquivo desejado
		ArrayList<String> peersL = new ArrayList<String>();

		//A partir daqui é necessário ter acesso à peerFiles
		Servidor.sem.acquire();
		
		//Itero sobre a lista do servidor e adiciono os peers que estão associados ao arquivo
		for (int i = 0; i < Servidor.peerFiles.size(); i++) {
			//pego o nome do arquivo das informacoes do servidor, que esta depois da ','
			String[] splits = Servidor.peerFiles.get(i).split(",");
			//Se splits.lenght == 0, é um peer sem arquivos
			if (splits.length > 1) {
				String peerFile = splits[1];

				if (peerFile.compareTo(fileName) == 0) {
					//Adiciono as informacoes relevantes do peer, ou seja, o ip e a porta tcp para o download

					//split = [ip; udpPort; tcpPort,arq.ext]
					String[] split = Servidor.peerFiles.get(i).split(":");
					String ipTcpPort = split[0] + ":" + split[2].split(",")[0];
					peersL.add(ipTcpPort);
				}
			}
		}

		//peerFiles ja foi usado
		Servidor.sem.release();

		//Converto o arraylist para array para enviar na resposta
		String peers[] = Servidor.arrayLisit2Array(peersL);

		//Envido a resposta do search com a lista de peers que possuem o arquivo
		Mensagem msg = new Mensagem("SEARCH", peers);
		Peer.sendMsg(Servidor.serverSocket, msg, ipAddress, port);
	}

	/**
	 * Função que indica que um peer na rede baixou um arquivo novo, portanto adiciona o regeistro na lista.
	 * @param fileName    Arquivo baixado pelo peer
	 * @param ipAddress   Ip do peer que baixou o arquivo
	 * @param port        Port udp do peer que baixou o arquivo
	 */
	private void update(String fileName, InetAddress ipAddress, int port) throws IOException, InterruptedException {
		String ipPort = ipAddress.getHostAddress() + ":" + port;

		//A partir daqui é necessário ter acesso à peerFiles
		Servidor.sem.acquire();
		
		//Preciso procurar o tcpPort do peer na informacoes do servidor
		String tcpPort = "";
		for (int i = 0; i < Servidor.peerFiles.size(); i++) {
			//dados do i-esimo peer estao guardados antes da  ','
			String iPeer = Servidor.peerFiles.get(i).split(",")[0];
			if (iPeer.contains(ipPort)) {
				//ip:idpPort:tcpPort
				tcpPort = iPeer.split(":")[2];
				break;
			}
		}

		//Adiciono o novo arquivo do peer na lista de arquivos
		Servidor.peerFiles.add(ipPort + ":" + tcpPort +  "," + fileName);

		//peerFiles ja foi usado
		Servidor.sem.release();

		//Envio o updateOk para o peer
		Mensagem answer = new Mensagem("UPDATE_OK");
		Peer.sendMsg(Servidor.serverSocket, answer, ipAddress, port);
	}
}

/** Classe que fica enviando requisicoes ALIVE para os peers na rede a cada 30 segundos para ver se continuam vivos */
class AliveSender extends Thread {

	public void run() {
		try {
			//Alive é enviado a cada 30s aproximadamente
			AliveSender.sleep(30000);

			//Inicio a lista de peers que receberão a req Alive
			ArrayList<String> networKPeers = new ArrayList<String>();
			networKPeers.clear();

			//A partir daqui é necessário ter acesso à peerFiles
			Servidor.sem.acquire();
			
			//Itero para ver os peers que estão conectados
			for (int i = 0; i < Servidor.peerFiles.size(); i++) {
				//Pego o ip:udpPorta do peer i
				String[] slices = Servidor.peerFiles.get(i).split(":");
				String iPeerIpPort = slices[0] + ":" + slices[1];
				
				if (i == 0) {
					networKPeers.add(iPeerIpPort);
				}

				//Procuro se o i-esimo peer ja esta na lista da rede
				boolean foundPeer = false;
				for (int j = 0; j < networKPeers.size(); j++) {
					if (networKPeers.get(j).compareTo(iPeerIpPort) == 0) {
						foundPeer = true;
						break;
					}					
				}
				//se não estiver o adiciono
				if (!foundPeer) {
					networKPeers.add(iPeerIpPort);
				}

			}

			//peerFiles ja foi usado
			Servidor.sem.release();

			//Envios as requisições alive para os peers que estao conectados
			for (int i = 0; i < networKPeers.size(); i++) {
				Mensagem msg = new Mensagem("ALIVE");

				//String vem no formato ip:udpPorta
				String[] ipPort = networKPeers.get(i).split(":");
				String ip = ipPort[0];
				int port = Integer.valueOf(ipPort[1]);

				Peer.sendMsg(Servidor.serverSocket, msg, InetAddress.getByName(ip), port);
			}

			//Espero por 3 segundos para os peers respoderem
			AliveSender.sleep(3000);

			//A partir daqui é necessário ter acesso à peerFiles
			Servidor.sem.acquire();

			//Elimino os dados dos peers que nao estao vivos
			for (int i = 0; i < networKPeers.size(); i++) {
				String peerI = networKPeers.get(i);
				//Se o peeri não esta vivo
				if (!Servidor.alivePeers.contains(peerI)) {

					String removedFiles = "";
					//Itero sobre peerFiles para remover os arquivos do peer morto
					for (int j = 0; j < Servidor.peerFiles.size(); j++) {
						if (Servidor.peerFiles.get(j).contains(peerI)) {
							//Correcao no indice para nao pular registros
							String fullRegister = Servidor.peerFiles.remove(j--);

							//nome do arquivo vem depois da virgula
							String[] splits = fullRegister.split(",");
							if (splits.length > 1) {
								removedFiles += splits[1] + " ";
							}
						}
					}
					System.out.println("Peer " + networKPeers.get(i) + " morto. Eliminando seus arquivos " + removedFiles);
				}
			}

			//peerFiles ja foi usado
			Servidor.sem.release();

			//Limpo o Array de alivePeers para a proxima iteração de ALIVE reqs
			Servidor.alivePeers.clear();

			AliveSender nextIt = new AliveSender();
			nextIt.start();
			this.interrupt();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}