package napster;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

//TODO: retiro os e.printstack trace dos try/catch?

/** Classe principal do peer com a <code>main</code> e funcoes auxiliares */
public class Peer {
	/**Socket usado para enviar e receber as requisicoes udp */
	protected static DatagramSocket udpSocket;
	/**Socket usado para o download de arquivos por tcp */
	protected static ServerSocket tcpSocket;
	private static InetAddress serverAddress;
	private static int serverPort;
	/** Timer usado para reenviar requisicoes join se o server nao responder */
	protected static TimeoutTimer joinTimer;
	/** Timer usado para reenviar requisicoes leave se o server nao responder */
	protected static TimeoutTimer leaveTimer;
	/** Timer usado para reenviar requisicoes update se o server nao responder */
	protected static TimeoutTimer updateTimer;
	/** Timer usado para reenviar requisicoes search se o server nao responder */
	protected static TimeoutTimer searchTimer;
	/** Pasta na maquina onde estao localizados os arquivos do peer e onde serao baixados os arquivos */
	protected static File storageFolder;
	/** booleano que controla se o peer se juntou a rede ou nao */
	protected static boolean joined = false;
	
	public static void main(String args[]) throws Exception{
		//buffer que le info do teclado
		BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));

		//abro o socket no ip e porta escolhido pelo usuario.
		//Enquanto houver algum problema para abrir o socket o
		//usuario deve digitar informacoes dnv
		boolean ipPortOk = false;
		while (!ipPortOk) {
			try {
				String peerIp = inFromUser.readLine();
				int udpPort = Integer.valueOf(inFromUser.readLine());
				udpSocket = new DatagramSocket(new InetSocketAddress(peerIp, udpPort));
				ipPortOk = true;
			} catch (Exception e) {
	
			}
		}

		System.out.println("Digite o caminho da pasta dos arquivos");
		storageFolder = new File(inFromUser.readLine());

		//Thread para ouvir requisições udp
		PeerListenerThread plt = new PeerListenerThread();

		//Inicio a thread que irá esperar por conexões de download
		DownloadListener dl = new DownloadListener();
		dl.start();

		//main do peer fica em loop ate o programa ser morto
		System.out.println("Digite a operação desejada: JOIN, LEAVE, SEARCH, DOWNLOAD");
		String userChoice = inFromUser.readLine();
		while (true) {
			switch(userChoice.toUpperCase()) {
				case "JOIN":
					if (!joined) {
						//Para o projeto o endereço do servido e sempre 127.0.0.1
						serverAddress = InetAddress.getByName("localhost");
						//Porta do servidor e sempre 10098
						serverPort = 10098;

						//Inicializa a thread do peer que trata requisições recebidas
						plt = new PeerListenerThread();
						plt.start();

						//Crio a pasta para armazenamento se não existir
						if (!storageFolder.exists()) {
							storageFolder.mkdir();
						}

						//Crio a lista com os nomes dos arquivos na pasta especificada
						//para armazenamento. A lista sera enviada para o servidor
						String[] fileNames;
						if (storageFolder.isDirectory()) {
							File[] folderFiles = storageFolder.listFiles();
							fileNames = new String[folderFiles.length + 1];

							//Primeiro campo da lista da mensagem deve ser a porta tcp do peer
							fileNames[0] = tcpSocket.getLocalPort() + "";

							for (int i = 1; i < fileNames.length; i++) {
								fileNames[i] = folderFiles[i - 1].getName();
							}
						} else {
							//primeiro campo envia o port do socket tcp sempre
							fileNames = new String[1];
							fileNames[0] = tcpSocket.getLocalPort() + "";
						}

						join(fileNames);
					}
					break;
				case "SEARCH":
					System.out.println("Qual o nome do arquivo desejado?");
					String fileName = inFromUser.readLine();
					if (joined) {
						search(fileName);
					}
					break;
				case "DOWNLOAD":
					System.out.println("Digite o IP do peer que possui o arquivo");
					InetAddress peerAddress = InetAddress.getByName(inFromUser.readLine());
					System.out.println("Digite o a porta tcp do peer que possui o arquivo");
					int peerPort = Integer.valueOf(inFromUser.readLine());
					System.out.println("Digite o nome do arquivo desejado");
					String file2download = inFromUser.readLine();
					
					//Download e feito em uma thread separada
					DownloadThread dt = new DownloadThread(peerAddress, peerPort, file2download);
					dt.start();
					break;
				case "LEAVE":
					if (joined) {
						leave();

						//interrompo a thread que trata requisicoes caso o peer deixe a rede
						if (!plt.isInterrupted()) {
							plt.interrupt();
						}
					}
					break;
			}
			System.out.println("Digite a operação desejada: JOIN, LEAVE, SEARCH, DOWNLOAD");
			userChoice = inFromUser.readLine();
		}
	}

	/**
	 * Funcao que recebe a lista de arquivo na pasta do peer e envia uma
	 * requisicao ao servidor para entrar na rede. Se o servidor nao responder
	 * a requisicao e reenviada
	 * @param fileNames   Lista de strings com os arquivos na pasta do peer
	*/
	protected static void join(String fileNames[]) throws IOException, ClassNotFoundException {
		//Instancio uma mensagem com o tipo de requisicao e passo a lista de arquivos que o peer tem para o servidor guardar os dados
		Mensagem msg = new Mensagem("JOIN", fileNames);
		sendMsg(udpSocket, msg, serverAddress, serverPort);

		//Uso uma classe de timer para reenviar a requisicao caso o servidor nao responda
		if (joinTimer != null) {
			joinTimer.stop = true;
		}
		joinTimer = new TimeoutTimer("join", fileNames);
		joinTimer.start();
	}

	/**
	 * Funcao que envia ao servidor uma sinalizacao pedindo para ser desconectado da rede.
	 * O servidor apagará os registro sobre o peer e deixara de enviar alive para ele. Se
	 * o servidor nao responder a requisicao e reenviada
	 */
	protected static void leave() throws IOException, ClassNotFoundException {
		//Instancio e envio a mensagem sinalizando que o peer deve sair da rede
		Mensagem msg = new Mensagem("LEAVE");
		sendMsg(udpSocket, msg, serverAddress, serverPort);

		//Uso uma classe de timer para reenviar a requisicao caso o servidor nao responda
		if (leaveTimer != null) {
			leaveTimer.stop = true;
		}
		leaveTimer = new TimeoutTimer("leave", null);
		leaveTimer.start();
	}

	/**
	 * Funcao que sinaliza envia uma mensagem ao servidor indicando que um arquivo novo foi
	 * baixado na pasta do peer
	 * @param newFile   String do arquivo novo baixado pelo peer
	 */
	protected static void update(String newFile) throws IOException, ClassNotFoundException {
		//Campo fileName da mensagem so tera um elemento, que e o nome do arquivo baixado
		String[] fileList = new String[1];
		fileList[0] = newFile;

		//instancio e envio a mensagem
		Mensagem msg = new Mensagem("UPDATE", fileList);
		sendMsg(udpSocket, msg, serverAddress, serverPort);

		//Uso uma classe de timer para reenviar a requisicao caso o servidor nao responda
		if (updateTimer != null) {
			updateTimer.stop = true;
		}
		updateTimer = new TimeoutTimer("update", fileList);
		updateTimer.start();
	}

	/**
	 * Funcao que envia uma mensagem ao servidor pedindo uma lista de peers que contem um certo arquivo
	 * @param fileName   Arquivo que o servidor vai procurar
	 */
	protected static void search(String fileName) throws IOException {
		//Campo fileName da mensagem so tera um elemento, que e o nome do arquivo a ser procurado
		String[] fileList = new String[1];
		fileList[0] = fileName;

		//Instancio a mensagem e envio para o servidor
		Mensagem msg = new Mensagem("SEARCH", fileList);
		sendMsg(udpSocket, msg, serverAddress, serverPort);

		//Uso uma classe de timer para reenviar a requisicao caso o servidor nao responda
		if (searchTimer!= null) {
			searchTimer.stop = true;
		}
		searchTimer = new TimeoutTimer("search", fileList);
		searchTimer.start();
	}

	/**
	 * Funcao que cria uma conexao tcp com outro peer para baixar um determinado arquivo
	 * na pasta informada pelo usuario quando o programa e iniciado. O outro peer pode
	 * enviar o arquivo ou negar o download
	 * @param peerAddress   Ip do peer que tem o arquivo desejado
	 * @param peerPort      Porta tcp do peer que tem o arquivo desejado
	 * @param fileName      Nome do arquivo desejado
	 */
	protected static void download(InetAddress peerAddress, int peerPort, String fileName) throws IOException, ClassNotFoundException {
		//Crio o socket para conexão TCP
		Socket downloadSocket = new Socket(peerAddress, peerPort);

		//canal de envio de dados do socket
		DataOutputStream dos = new DataOutputStream(downloadSocket.getOutputStream());

		//canal que le dados que o socket recebe
		DataInputStream dis = new DataInputStream(downloadSocket.getInputStream());
		
		//Crio a mensagem para requisitar o download. Seu campo fileNames apenas contem o nome do
		//arquivo que deve ser baixado
		String[] fileList = new String[1];
		fileList[0] = fileName;
		Mensagem msg = new Mensagem("DOWNLOAD", fileList);

		//envio a requisição do download atraves do socket. primeiro envio o tamanho da mensagem
		//para o outro peer saber quantos bytes tera que ler
		byte[] msgBytes = MsgtoBytes(msg);
		dos.writeInt(msgBytes.length);
		dos.write(msgBytes);

		//leito a resposta do outro peer. 1 significa que o download foi aprovado e 0 quer dizer
		//que foi negado
		int answertype = dis.readInt();
		if (!(answertype == 0)) { //DOWNLOAD APROVADO
			//Preparo o novo arquivo e o canal que ira escrever os dados nele
			File newFile = new File(storageFolder, fileName);
			FileOutputStream fos = new FileOutputStream(newFile);
			//logo depois de o outro peer aprovar a mensagem, ira mandar o tamanho do arquivo
			long fileLength = dis.readLong();

			//O outro peer ira mandar o arquivo em pedaços de 4KB
			byte[] buffer = new byte[4*1024];
			long remainingSize = fileLength;
			//Quantidade de bytes que sera lida na proxima iteracao do loop while. Se ainda
			//houver dados faltando do que cabe no buffer, entao a capacidade do buffer que
			//deve ser usada
			int bytes = dis.read(buffer, 0, (int) Math.min(buffer.length, remainingSize));
			//loop que dura enquanto houver bytes para serem lidos de acordo com o tamanho
			//do arquivo informado pelo outro peer
			while(remainingSize > 0 && bytes != -1) {
				fos.write(buffer, 0, bytes);
				remainingSize -= bytes;
				bytes = dis.read(buffer, 0, (int) Math.min(buffer.length, remainingSize));
			}
			fos.close();

			System.out.println("Arquivo " + fileName + " baixado com sucesso na pasta " + Peer.storageFolder);

			//Como foi possivel baixar o arquivo, mando requisição de atualizacao para o servidor
			update(fileName);
		} else {
			System.out.println("peer " + peerAddress.getHostAddress() + ":" + peerPort + " negou o download" /*TODO: pedindo agora para... [ip]:[porta] */);
		}

		downloadSocket.close();
	}

	/** Funcao que envia ao servidor a resposta ao alive para indicar que este peer ainda esta operando */
	protected static void aliveOk() throws IOException {
		Mensagem answer = new Mensagem("ALIVE_OK");
		sendMsg(udpSocket, answer, serverAddress, serverPort);
	}

	/**
	 * Funcao auxiliar que utiliza um socket udp para enviar uma mensagem qualquer a um destinatario qualquer
	 * @param udpSocket    Socket que sera usado para enviar a mensagem e recebera possiveis respostas
	 * @param msg          Mensagem que deve ser enviada
	 * @param ipAddress    Endereco de ip do destinatario
	 * @param port         Porta do destinatario
	 */
	static void sendMsg(DatagramSocket udpSocket, Mensagem msg, InetAddress ipAddress, int port) throws IOException {
		//buffer com os bytes do pacote udp
		byte[] sendData = new byte[1024];
		//Converte um objeto de Mensagem para bytes
		sendData = MsgtoBytes(msg);

		//Crio o pacote para o destinatario e mando atraves do socket informado
		DatagramPacket udpPacket = new DatagramPacket(sendData, sendData.length, ipAddress, port);
		udpSocket.send(udpPacket);
	}

	/**
	 * Funcao que converte um objeto da classe Mensagem para uma sequencia de bytes.
	 * Util para criar os pacotes e trocar informacoes entre os sockets
	 * @param msg   Objeto da classe mensagem que sera convertido
	 * @return      Retorna um array de bytes que podera ser desconvertido depois que viajar de um socket ao outro
	 */
	static byte[] MsgtoBytes(Mensagem msg) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		//Classe do java que pode converter objetos para bytes
		ObjectOutputStream oos = new ObjectOutputStream(baos);
		oos.writeObject(msg);
		oos.close();
		return baos.toByteArray();
	}

	/**
	 * Funcao que desconverte uma sequencia de bytes de volta para o Objeto em java.
	 * @param bytes   Resultado da conversao de MsgtoBytes
	 * @return        O objeto Mensagem que havia sido inicialmente convertido
	 */
	static Mensagem BytestoMsg(byte[] bytes) throws IOException, ClassNotFoundException {
		ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
		ObjectInputStream ois = new ObjectInputStream(bais);
		Mensagem msg = (Mensagem) ois.readObject();
		ois.close();
		return msg;
	}

	/**
	 * Funcao auxiliar que concatena um array de strings em uma string so, separando cada
	 * elemento com um espaco branco
	 * @param array    Array de strings que sera concatenado
	 * @return             String com o array concatenado. Retorna <code>null</code> se a lista recebida for nula
	 */
	static String stringArrayConcat(String[] array) {
		if (array == null) {
			return "";
		}
		String concat = "";
		for (int i = 0; i < array.length; i++) {
			concat += array[i] + " ";
		}
		//remove o ultimo espaço
		//concat = concat.substring(0, concat.length() - 2);
		return concat;
	}
}

/** Classe que ficará em loop esperando por mensagens udp para criar threads de antendimento individuais uma vez que as mensagens chegarem. */
class PeerListenerThread extends Thread {

	public void run() {
		try {
			//Socket ja esta criado no peer
			DatagramSocket masterSocket = Peer.udpSocket;

			while(true) {
				//buffer que receberá os dados do pacote
				byte[] recBuffer = new byte[1024];

				//inicializo as variaveis do pacote
				DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);

				masterSocket.receive(recPkt); //BLOCKING
				
				//Encaminho o pacote para uma thread separada realizar o atendimento
				PeerAnswerThread pat = new PeerAnswerThread(recPkt);
				pat.start();
			}
		} catch (Exception e) {

		}
	}
}

/** Classe que realiza o tratamento individual de uma requisicao UDP que chegar */
class PeerAnswerThread extends Thread {
	private DatagramPacket pkt;

	public PeerAnswerThread(DatagramPacket pkt) {
		this.pkt = pkt;
	}

	public void run() {
		try {
			//Converte os dados do pacote de volta para uma Mensagem
			Mensagem msg = Peer.BytestoMsg(pkt.getData());

			//Cada vez que um ok chega é necessário parar o respectivo timer que estará contando na
			//classe Peer para que não de o timeout e a mensagem não seja enviada outra vez
			switch(msg.reqtype) {
				case "JOIN_OK":
					Peer.joinTimer.stop = true;
					Peer.joined = true;
					System.out.println("Sou peer " + Peer.udpSocket.getLocalAddress().getHostAddress() + ":" + Peer.udpSocket.getLocalPort() + " com arquivos " + Peer.stringArrayConcat(msg.fileNames));
					break;
				case "LEAVE_OK":
					Peer.leaveTimer.stop = true;
					Peer.joined = false;
					break;
				case "UPDATE_OK":
					Peer.updateTimer.stop = true;
					break;
				case "SEARCH":
					Peer.searchTimer.stop = true;
					//Na resposta search, o campo fileNames da mensagem condera um array com os
					//peers que possuem o arquivo
					System.out.println("peers com arquivo solicitado: [" + Peer.stringArrayConcat(msg.fileNames) + "]");
					break;
				case "ALIVE":
					//e necessario enviar de volta ao servidor um aliveOk
					Peer.aliveOk();
					break;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

/** Thread que esperará por pedidos de download e criara uma outra thread separada para cada
 * pedido que chegar */
class DownloadListener extends Thread {
	public void run() {
		try {
			//Socket usado é um atributo do peer. Dessa forma o SO pode escolher a porta que o socket terá
			Peer.tcpSocket = new ServerSocket(0);

			//loop que aguarda conexoes e as manda para uma thread separada
			while(true) {
				Socket node = Peer.tcpSocket.accept();

				FileSenderThread fst = new FileSenderThread(node);
				fst.start();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

/** Thread usada pelo peer quando este precisa mandar um arquivo a outro peer */
class FileSenderThread extends Thread {

	/**Socket tcp da conexao criada pelo listener*/
	private Socket node;

	public FileSenderThread(Socket node) {
		this.node = node;
	}

	public void run() {
		try {
			//Canal para ler informações do socket
			DataInputStream dis = new DataInputStream(node.getInputStream());

			//Enviar informações pelo socket
			DataOutputStream dos = new DataOutputStream(node.getOutputStream());

			//Primeiro o outro peer ira mandar o tamanho da mensagem para saber quando sera necessario ler
			int msgLength = dis.readInt();
			if (msgLength > 0) {
				//Agora deve ser lido a quantidade de bytes que a mensagem tem
				byte[] msgBytes = new byte[msgLength];
				dis.readFully(msgBytes, 0, msgLength);

				//Bytes sao convertidos de vola ao objeto
				Mensagem msg = Peer.BytestoMsg(msgBytes);

				//Arquivo que sera enviado devera vir da pasta do peer e seu noma esta na primeira
				//posicao do campo fileNames 
				File fileToSend = new File(Peer.storageFolder, msg.fileNames[0]);

				//Nego o download se o arquivo não existir
				//ou aleatoriamente nego o download requisitado
				//Random rd = new Random();
				if (!fileToSend.exists() /*TODO: || rd.nextInt(100) < 50*/) {
					//Envio o download negado por TCP
					Mensagem answer = new Mensagem("DOWNLOAD_NEGADO");

					byte[] answerBytes = Peer.MsgtoBytes(answer);
					//0 sinaliza ao outro peer que o download foi negado
					dos.writeInt(0);
					dos.writeInt(answerBytes.length);
					dos.write(answerBytes);
				} else {
					FileInputStream fis = new FileInputStream(fileToSend.getAbsolutePath());

					//1 sinaliza ao outro peer que o download foi aprovado
					dos.writeInt(1);
					//escrevo no canal do socket o tamanho do arquivo
					dos.writeLong(fileToSend.length());

					//Arquivo sera enviado em pedacos de 4KB
					byte[] buffer = new byte[4*1024];
					//Quantidade de bytes que sera enviada. fis.read() lera os proximos 4KB, colocara no buffer. a funcao retorna -1 quando nao restar mais nada para ser lido
					int bytes = fis.read(buffer);
					while ( bytes != -1) {
						//Escrevo exatamente os bytes do buffer no canal do socket
						dos.write(buffer, 0, bytes);
						dos.flush();
						//Leio o proximo pedaco
						bytes = fis.read(buffer);
					}
				}

			}
			node.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

/**Thread utilizada pelo peer quando este precisa baixar o arquivo de outro peer */
class DownloadThread extends Thread {
	/**Endereco do peer que tem o arquivo desejado */
	private InetAddress peerAddr;
	/** Porta tcp do peer que tem o arquivo */
	private int peerPort;
	/**Nome do arquivo que o peer quer baixar */
	private String file2download;

	public DownloadThread(InetAddress peerAddr, int peerPort, String file2download) {
		this.peerAddr = peerAddr;
		this.peerPort = peerPort;
		this.file2download = file2download;
	}

	public void run () {
		try {
			//Apenas e necessario chamar a funcao do peer que faz o download
			Peer.download(peerAddr, peerPort, file2download);
		} catch (ConnectException e) {
			//TODO: talvez tirar esse print
			System.out.println("Não foi possível se conectar. Verifique se o IP e a Porta foram digitados corretamente.");
		} catch (Exception e) {
			e.printStackTrace();
		};
	}
}

/** Thread que funciona como timer para as requisicoes que o peer enviada. Ha uma thread dessa
 * como atributo no peer para cada requisicao que ele pode enviar. A classe apenas chama novamente
 * o metodo implementado no peer caso nao tenha sido sinalizada para parar durante o tempo
 * que esta dormindo.*/
class TimeoutTimer extends Thread {
	/** String que diz qual dos metodos implementados no peer ddeve ser chamado*/
	private String invoke;
	/** Lista com os dados da mensagem. Equivalente ao atributo Mensagem.fileNames */
	private String[] msgData;
	/** Booleano que pode ser alterado durante o tempo que a thread dorme.
	 * Se na for, é considerado que ocorreu um timeout e o metodo do peer e chamado outra vez */
	protected boolean stop;

	public TimeoutTimer(String method, String msgData[]) {
		invoke = method;
		this.msgData = msgData;
		stop = false;
	}

	public void run() {
		try {
			//Tempo de timeout de 4 segundos
			TimeoutTimer.sleep(4000);

			//Interrompo a thread se foi sinalizado pelo peer que a respota chegou
			if (stop) {
				this.interrupt();
				return;
			}

			//Chamo o metodo correspondete no peer de acordo o metodo informado no instante
			//em que a classe e instanciada
			switch(invoke) {
				case "join":
					Peer.join(msgData);
					break;
				case "leave":
					Peer.leave();
					break;
				case "update":
					Peer.update(msgData[0]);
					break;
				case "search":
					Peer.search(msgData[0]);
					break;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}