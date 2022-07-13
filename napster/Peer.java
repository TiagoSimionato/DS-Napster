package napster;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class Peer {
	static DatagramSocket udpSocket;
	private static InetAddress serverAddress;
	private static int serverPort;
	public static void main(String args[]) throws Exception{
		//buffer que le info do teclado
		BufferedReader inFromUser = new BufferedReader(
									new InputStreamReader(System.in));

		//abro o socket
		udpSocket = new DatagramSocket();

		String userChoice = inFromUser.readLine();
		while (userChoice.compareTo("LEAVE") != 0) {
			switch(userChoice) {
				case "JOIN":
					//Inicializa o peer com as informações necessárias do teclado
					serverAddress = InetAddress.getByName(inFromUser.readLine());
					serverPort = Integer.valueOf(inFromUser.readLine());
					File storageFolder = new File(inFromUser.readLine());

					//Inicializo a thread do peer que recebe requisições
					PeerThread pt = new PeerThread();
					pt.start();

					//Crio a pasta para armazenamento se não existir
					if (!storageFolder.exists()) {
						storageFolder.mkdir();
					}

					//Crio a lista com os nomes dos arquivos na pasta especificada para armazenamento
					String[] fileNames;
					if (storageFolder.isDirectory()) {
						File[] filePaths = storageFolder.listFiles();
						fileNames = new String[filePaths.length];

						for (int i = 0; i < filePaths.length; i++) {
							fileNames[i] = filePaths[i].getName();
						}
					} else {
						fileNames = null;
					}

					join(fileNames);
				case "SEARCH":
					String fileName = inFromUser.readLine();
					search(fileName);
				case "DOWNLOAD":
					InetAddress peerAddress = InetAddress.getByName(inFromUser.readLine());
					int peerPort = Integer.valueOf(inFromUser.readLine());
					String file2download = inFromUser.readLine();
					
					download(peerAddress, peerPort, file2download);
				case "LEAVE":
					leave();
			}
		}
	}

	private static void join(String fileNames[]) throws IOException, ClassNotFoundException {
		Mensagem msg;
		do {
			msg = new Mensagem("JOIN", fileNames);
			sendMsg(msg, serverAddress, serverPort);

			byte[] recBuffer = new byte[1024];
			DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
			udpSocket.receive(recPkt); //BLOCKING

			msg = BytestoMsg(recPkt.getData());
		} while (msg.reqtype.compareTo("JOIN_OK") != 0);
	}

	private static void leave() throws IOException, ClassNotFoundException {
		Mensagem msg;
		do {
			msg = new Mensagem("LEAVE");
			sendMsg(msg, serverAddress, serverPort);

			byte[] recBuffer = new byte[1024];
			DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
			udpSocket.receive(recPkt); //BLOCKING

			msg = BytestoMsg(recPkt.getData());
		} while (msg.reqtype.compareTo("LEAVE_OK") != 0);
	}

	private static void update(String newFile) throws IOException, ClassNotFoundException {
		Mensagem msg;
		do {
			String[] fileList = new String[1];
			fileList[0] = newFile;
			msg = new Mensagem("UPDATE", fileList);
			sendMsg(msg, serverAddress, serverPort);

		byte[] recBuffer = new byte[1024];
			DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
			udpSocket.receive(recPkt); //BLOCKING

			msg = BytestoMsg(recPkt.getData());
		} while (msg.reqtype.compareTo("LEAVE_OK") != 0);
	}

	private static void search(String fileName) throws IOException {
		String[] fileList = new String[1];
		fileList[0] = fileName;
		Mensagem msg = new Mensagem("SEARCH", fileList);
		sendMsg(msg, serverAddress, serverPort);
	}

	private static void download(InetAddress peerAddress, int peerPort, String fileName) throws IOException, ClassNotFoundException {
		//Crio o socket para conexão TCP
		Socket downloadSocket = new Socket(peerAddress, peerPort);

		//canal de envio de dados do socket
		OutputStream os =  downloadSocket.getOutputStream();
		DataOutputStream writer = new DataOutputStream(os);

		//canal que le dados que o socket recebe
		InputStreamReader is = new InputStreamReader(downloadSocket.getInputStream());
		BufferedReader reader = new BufferedReader(is);

		//Crio a mensagem para requisitar o download
		String[] fileList = new String[1];
		fileList[0] = fileName;
		Mensagem msg = new Mensagem("DOWNLOAD", fileList);

		//escrita no socket
		writer.write(MsgtoBytes(msg));;

		//leito a resposta do outro peer
		String response = reader.readLine(); //BLOCKING
		if (response != "DOWNLOAD_NEGADO") {
			//Se foi possivel baixar o arquivo, mando requisição de atualizacao para o servidor
			update(response);
		}

		downloadSocket.close();
	}

	static void sendMsg(Mensagem msg, InetAddress ipAddress, int port) throws IOException {
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
}

class PeerThread extends Thread {

	public void run() {
		try {
			DatagramSocket masterSocket = Peer.udpSocket;
			//ServerSocket masterSocket = new ServerSocket(port);

			while(true) {
				byte[] recBuffer = new byte[1024];

				DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);

				masterSocket.receive(recPkt); //BLOCKING
				
				//Socket answerSocket = masterSocket.accept();
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

	}
}