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
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Random;

//TODO: ARRUMAR IMPRESSAO DAS PORTAS
//TODO: BIND EXCEPTION ADDR ALREADY IN USE

public class Peer {
	protected static DatagramSocket udpSocket;
	protected static ServerSocket listenerSocket;
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

		//abro o socket
		udpSocket = new DatagramSocket();
		//TODO: BIND IP
		System.out.println("Peer " + udpSocket.getLocalAddress() + ":" + udpSocket.getLocalPort() + " iniciado");

		System.out.println("Digite a operação desejada: JOIN, LEAVE, SEARCH, DOWNLOAD");
		String userChoice = inFromUser.readLine();
		while (true) {
			switch(userChoice) {
				case "JOIN":
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
					PeerThread pt = new PeerThread();
					pt.start();

					//Crio a pasta para armazenamento se não existir
					if (!storageFolder.exists()) {
						storageFolder.mkdir();
					}

					//Crio a lista com os nomes dos arquivos na pasta especificada para armazenamento
					String[] fileNames;
					if (storageFolder.isDirectory()) {
						File[] folderFiles = storageFolder.listFiles();
						fileNames = new String[folderFiles.length];

						for (int i = 0; i < folderFiles.length; i++) {
							fileNames[i] = folderFiles[i].getName();
						}
					} else {
						fileNames = null;
						//System.out.println(("Deve ser digitado um diretório!"));
					}

					join(fileNames);
					break;
				case "SEARCH":
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
					
					download(peerAddress, peerPort, file2download);
					break;
				case "LEAVE":
					leave();
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

	private static void download(InetAddress peerAddress, int peerPort, String fileName) throws IOException, ClassNotFoundException {
		//Crio o socket para conexão TCP
		Socket downloadSocket = new Socket(peerAddress, 12345);

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
		System.out.println("Requisição de download enviada");

		//leito a resposta do outro peer
		int answertype = dis.readInt();
		System.out.println("Tipo de resposta lido: " + answertype);
		if (!(answertype == 0)) { //DOWNLOAD APROVADO
			//Preparo o novo arquivo e o canal que ira escrever os dados nele
			File newFile = new File(storageFolder, fileName);
			FileOutputStream fos = new FileOutputStream(newFile);

			System.out.println("\nDOWNLOAD APROVADO\n");

			long fileLength = dis.readLong();
			System.out.println("Li o tamanho do arquivo");

			byte[] buffer = new byte[4*1024];
			long remainingSize = fileLength;
			int bytes = dis.read(buffer, 0, (int) Math.min(buffer.length, remainingSize));
			while(remainingSize > 0 && bytes != -1) {
				fos.write(buffer, 0, bytes);
				remainingSize -= bytes;
				bytes = dis.read(buffer, 0, (int) Math.min(buffer.length, remainingSize));
			}
			System.out.println("Li o arquivo");
			fos.close();

			System.out.println("Arquivo baixado com sucesso");

			//Se foi possivel baixar o arquivo, mando requisição de atualizacao para o servidor
			//update(response);
		} else {
			System.out.println("Download Negado");
		}

		//downloadSocket.close();
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
		concat = concat.substring(0, concat.length() - 2);
		return concat;
	}
}

class PeerThread extends Thread {

	public void run() {
		try {
			DatagramSocket masterSocket = Peer.udpSocket;

			//Inicio a thread que irá esperar por conexões de download
			DownloadListener dl = new DownloadListener();
			dl.start();

			while(true) {
				byte[] recBuffer = new byte[1024];

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

class DownloadListener extends Thread {
	public void run() {
		try {
			Peer.listenerSocket = new ServerSocket(12345);

			while(true) {
				Socket node = Peer.listenerSocket.accept();
				System.out.println("Alguem pediu por um arquivo");

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
				System.out.println("Requisição de download recebida");


				File fileToSend = new File(Peer.storageFolder, msg.fileNames[0]);
				//TODO: REMOVE PRINT 
				System.out.println("File path: " + fileToSend.getAbsolutePath());
				System.out.println("File Name: " + fileToSend.getName());

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
					System.out.println("download negado com sucesso");
				} else {
					FileInputStream fis = new FileInputStream(fileToSend.getAbsolutePath());
					//Porta no qual o outro socket foi criada é enviada na requisição de download
					//Socket sendSocket = new Socket(pkt.getAddress(), Integer.valueOf(msg.fileNames[1]));
					//É nesse endereço/porta que o outro socket vai tentar se conectar
					//sendSocket.bind(new InetSocketAddress(InetAddress.getByName("localhost"), 12345));

					dos.writeInt(1);
					dos.writeLong(fileToSend.length());

					byte[] buffer = new byte[4*1024];
					int bytes = fis.read(buffer);
					while ( bytes != -1) {
						dos.write(buffer, 0, bytes);
						dos.flush();
						bytes = fis.read(buffer);
					}

					System.out.println("arquivo enviado com sucesso");
				}

			}
			//node.close();
		} catch (Exception e) {
			e.printStackTrace();
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
			System.out.println("Mensagem " + msg.reqtype + " recebida de: " + pkt.getSocketAddress());

			switch(msg.reqtype) {
				case "JOIN_OK":
					Peer.joinTimer.stop = true;
					System.out.println("Sou peer " + Peer.udpSocket.getLocalAddress().getHostAddress() + ":" + Peer.udpSocket.getLocalPort() + " com arquivos " + Peer.stringArrayConcat(msg.fileNames));
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
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		
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