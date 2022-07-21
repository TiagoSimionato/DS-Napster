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

public class Peer {
	protected static DatagramSocket udpSocket;
	protected static ServerSocket tcpSocket;
	private static InetAddress serverAddress;
	private static int serverPort;
	protected static TimeoutTimer joinTimer;
	protected static TimeoutTimer leaveTimer;
	protected static TimeoutTimer updateTimer;
	protected static TimeoutTimer searchTimer;
	protected static File storageFolder;
	public static void main(String args[]) throws Exception{
		//buffer que le info do teclado
		BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));

		//abro o socket no ip 127.0.0.1 e porta definida pelo SO
		udpSocket = new DatagramSocket(new InetSocketAddress(InetAddress.getByName("localhost"), 0));
		
		System.out.println("Peer " + udpSocket.getLocalAddress() + ":" + udpSocket.getLocalPort() + " iniciado");

		//Thread para ouvir requisições udp
		PeerListenerThread pt = new PeerListenerThread();

		//Inicio a thread que irá esperar por conexões de download
		DownloadListener dl = new DownloadListener();
		dl.start();

		System.out.println("Digite a operação desejada: JOIN, LEAVE, SEARCH, DOWNLOAD");
		String userChoice = inFromUser.readLine();
		while (true) {
			switch(userChoice) {
				case "JOIN":
					//TODO: coletar dados corretamente
					//Captura as informações necessárias do teclado
					System.out.println("Digite o ip do server");
					//serverAddress = InetAddress.getByName(inFromUser.readLine());
					serverAddress = InetAddress.getByName("localhost");
					System.out.println("Digite a porta do server");
					//serverPort = Integer.valueOf(inFromUser.readLine());
					serverPort = 10098;
					System.out.println("Digite o caminho da sua pasta");
					storageFolder = new File(inFromUser.readLine());

					//Inicializa a thread do peer que recebe e trata requisições
					pt = new PeerListenerThread();
					pt.start();

					//Crio a pasta para armazenamento se não existir
					if (!storageFolder.exists()) {
						storageFolder.mkdir();
					}

					//Crio a lista com os nomes dos arquivos na pasta especificada para armazenamento
					String[] fileNames;
					if (storageFolder.isDirectory()) {
						File[] folderFiles = storageFolder.listFiles();
						fileNames = new String[folderFiles.length + 1];

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
					break;
				case "SEARCH":
					System.out.println("Qual o nome do arquivo desejado?");
					String fileName = inFromUser.readLine();
					search(fileName);
					break;
				case "DOWNLOAD":
					System.out.println("Digite o IP do peer que possui o arquivo");
					InetAddress peerAddress = InetAddress.getByName(inFromUser.readLine());
					System.out.println("Digite o a porta do peer que possui o arquivo");
					int peerPort = Integer.valueOf(inFromUser.readLine());
					System.out.println("Digite o nome do arquivo desejado");
					String file2download = inFromUser.readLine();
					
					DownloadThread dt = new DownloadThread(peerAddress, peerPort, file2download);
					dt.start();
					break;
				case "LEAVE":
					leave();
					if (!pt.isInterrupted()) {
						pt.interrupt();
					}
					break;
			}
			System.out.println("Digite a operação desejada: JOIN, LEAVE, SEARCH, DOWNLOAD");
			userChoice = inFromUser.readLine();
		}
	}

	protected static void join(String fileNames[]) throws IOException, ClassNotFoundException {
		Mensagem msg = new Mensagem("JOIN", fileNames);
		sendMsg(udpSocket, msg, serverAddress, serverPort);

		if (joinTimer != null) {
			joinTimer.interrupt();
		}
		joinTimer = new TimeoutTimer("join", fileNames);
		joinTimer.start();
	}

	protected static void leave() throws IOException, ClassNotFoundException {
		Mensagem msg = new Mensagem("LEAVE");
		sendMsg(udpSocket, msg, serverAddress, serverPort);
		if (leaveTimer != null) {
			leaveTimer.interrupt();
		}
		leaveTimer = new TimeoutTimer("leave", null);
		leaveTimer.start();
	}

	protected static void update(String newFile) throws IOException, ClassNotFoundException {
		String[] fileList = new String[1];
		fileList[0] = newFile;
		Mensagem msg = new Mensagem("UPDATE", fileList);
		sendMsg(udpSocket, msg, serverAddress, serverPort);
		if (updateTimer != null) {
			updateTimer.interrupt();
		}
		updateTimer = new TimeoutTimer("update", fileList);
		updateTimer.start();
	}

	protected static void search(String fileName) throws IOException {
		String[] fileList = new String[1];
		fileList[0] = fileName;
		Mensagem msg = new Mensagem("SEARCH", fileList);
		sendMsg(udpSocket, msg, serverAddress, serverPort);
		if (searchTimer!= null) {
			searchTimer.interrupt();
		}
		searchTimer = new TimeoutTimer("search", fileList);
		searchTimer.start();
	}

	protected static void download(InetAddress peerAddress, int peerPort, String fileName) throws IOException, ClassNotFoundException {
		//Crio o socket para conexão TCP
		Socket downloadSocket = new Socket(peerAddress, peerPort);

		//canal de envio de dados do socket
		DataOutputStream dos = new DataOutputStream(downloadSocket.getOutputStream());

		//canal que le dados que o socket recebe
		DataInputStream dis = new DataInputStream(downloadSocket.getInputStream());
		
		//Crio a mensagem para requisitar o download
		String[] fileList = new String[1];
		fileList[0] = fileName;
		Mensagem msg = new Mensagem("DOWNLOAD", fileList);

		//envio a requisição do download atraves do socket
		byte[] msgBytes = MsgtoBytes(msg);
		dos.writeInt(msgBytes.length);
		dos.write(msgBytes);

		//leito a resposta do outro peer
		int answertype = dis.readInt();
		if (!(answertype == 0)) { //DOWNLOAD APROVADO
			//Preparo o novo arquivo e o canal que ira escrever os dados nele
			File newFile = new File(storageFolder, fileName);
			FileOutputStream fos = new FileOutputStream(newFile);
			long fileLength = dis.readLong();

			byte[] buffer = new byte[4*1024];
			long remainingSize = fileLength;
			int bytes = dis.read(buffer, 0, (int) Math.min(buffer.length, remainingSize));
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
			System.out.println("peer " + peerAddress.getHostAddress() + ":" + peerPort + "negou o download" /*TODO: pedindo agora para... [ip]:[porta] */);
		}

		downloadSocket.close();
	}

	protected static void aliveOk() throws IOException {
		Mensagem answer = new Mensagem("ALIVE_OK");
		sendMsg(udpSocket, answer, serverAddress, serverPort);
	}

	static void sendMsg(DatagramSocket udpSocket, Mensagem msg, InetAddress ipAddress, int port) throws IOException {
		byte[] sendData = new byte[1024];
		sendData = MsgtoBytes(msg);

		DatagramPacket udpPacket = new DatagramPacket(sendData, sendData.length, ipAddress, port);

		udpSocket.send(udpPacket);
	}

	static byte[] MsgtoBytes(Mensagem msg) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(baos);
		oos.writeObject(msg);
		oos.close();
		return baos.toByteArray();
	}

	static Mensagem BytestoMsg(byte[] bytes) throws IOException, ClassNotFoundException {
		ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
		ObjectInputStream ois = new ObjectInputStream(bais);
		Mensagem msg = (Mensagem) ois.readObject();
		ois.close();
		return msg;
	}

	static String stringArrayConcat(String[] fileNames) {
		if (fileNames == null) {
			return "";
		}
		String concat = "";
		for (int i = 0; i < fileNames.length; i++) {
			concat += fileNames[i] + " ";
		}
		//remove o ultimo espaço
		//concat = concat.substring(0, concat.length() - 2);
		return concat;
	}
}

//Classe que ficará em loop esperando por mensagens udp para criar threads de antendimento individuais uma vez que as mensagens chegarem. Além disso também inicializa junto com ela a thread que espera por requisições de download
class PeerListenerThread extends Thread {

	public void run() {
		try {
			DatagramSocket masterSocket = Peer.udpSocket;

			while(true) {
				//buffer que receberá os dados do pacote
				byte[] recBuffer = new byte[1024];

				//inicializo as variaveis do pacote
				DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);

				masterSocket.receive(recPkt); //BLOCKING
				
				//Encaminho para uma thread separada realizaro atendimento
				PeerAnswerThread pat = new PeerAnswerThread(recPkt);
				pat.start();
			}
		} catch (Exception e) {

		}
	}
}

class PeerAnswerThread extends Thread {
	private DatagramPacket pkt;

	public PeerAnswerThread(DatagramPacket pkt) {
		this.pkt = pkt;
	}

	public void run() {
		try {
			Mensagem msg = Peer.BytestoMsg(pkt.getData());

			//Cada vez que um ok chega é necessário parar o timer que estará contando na classe Peer para que não de o timeout e a mensagem não seja enviada outra vez
			switch(msg.reqtype) {
				case "JOIN_OK":
					Peer.joinTimer.stop = true;
					System.out.println("Sou peer " + Peer.udpSocket.getLocalAddress().getHostAddress() + ":" + Peer.udpSocket.getLocalPort() + ":" + Peer.tcpSocket.getLocalPort() + " com arquivos " + Peer.stringArrayConcat(msg.fileNames));
					break;
				case "LEAVE_OK":
					Peer.leaveTimer.stop = true;
					break;
				case "UPDATE_OK":
					Peer.updateTimer.stop = true;
					break;
				case "SEARCH":
					Peer.searchTimer.stop = true;
					System.out.println("peers com arquivo solicitado: [" + Peer.stringArrayConcat(msg.fileNames) + "]");
					break;
				case "ALIVE":
					Peer.aliveOk();
					break;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		
	}
}

class DownloadListener extends Thread {
	public void run() {
		try {
			Peer.tcpSocket = new ServerSocket(0);

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

class FileSenderThread extends Thread {

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

			int msgLength = dis.readInt();
			if (msgLength > 0) {
				//Recebo a requisição do download
				byte[] msgBytes = new byte[msgLength];
				dis.readFully(msgBytes, 0, msgLength);

				Mensagem msg = Peer.BytestoMsg(msgBytes);


				File fileToSend = new File(Peer.storageFolder, msg.fileNames[0]);

				//Nego o download se o arquivo não existir
				//ou aleatoriamente nego o download requisitado
				Random rd = new Random();
				if (!fileToSend.exists() /*TODO: || rd.nextInt(100) < 50*/) {
					//Envio o download negado por TCP
					Mensagem answer = new Mensagem("DOWNLOAD_NEGADO");

					byte[] answerBytes = Peer.MsgtoBytes(answer);
					dos.writeInt(0);
					dos.writeInt(answerBytes.length);
					dos.write(answerBytes);
				} else {
					FileInputStream fis = new FileInputStream(fileToSend.getAbsolutePath());

					dos.writeInt(1);
					dos.writeLong(fileToSend.length());

					byte[] buffer = new byte[4*1024];
					int bytes = fis.read(buffer);
					while ( bytes != -1) {
						dos.write(buffer, 0, bytes);
						dos.flush();
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

class DownloadThread extends Thread {

	private InetAddress peerAddr;
	private int peerPort;
	private String file2download;

	public DownloadThread(InetAddress peerAddr, int peerPort, String file2download) {
		this.peerAddr = peerAddr;
		this.peerPort = peerPort;
		this.file2download = file2download;
	}

	public void run () {
		try {
			Peer.download(peerAddr, peerPort, file2download);
		} catch (ConnectException e) {
			//e.printStackTrace();
			System.out.println("Não foi possível se conectar. Verifique se o IP e a Porta foram digitados corretamente.");
		} catch (Exception e) {
			e.printStackTrace();
		};
	}
}

class TimeoutTimer extends Thread {
	private String invoke;
	private String[] fileData;
	protected boolean stop;

	public TimeoutTimer(String method, String fileData[]) {
		invoke = method;
		this.fileData = fileData;
		stop = false;
	}

	public void run() {
		try {
			TimeoutTimer.sleep(4000);
			if (stop) {
				this.interrupt();
				return;
			}

			switch(invoke) {
				case "join":
					Peer.join(fileData);
					break;
				case "leave":
					Peer.leave();
					break;
				case "update":
					Peer.update(fileData[0]);
					break;
				case "search":
					Peer.search(fileData[0]);
					break;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}