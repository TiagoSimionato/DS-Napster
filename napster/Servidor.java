package napster;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class Servidor {
	public static void main(String args[]) throws Exception{
		DatagramSocket serverSocket = new DatagramSocket(10098);

		while(true) {
			byte[] recBuffer = new byte[1024];

			DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);

			serverSocket.receive(recPkt); //BLOCKING
			
			//Socket newConnection = serverSocket.accept(); //BLOCKING

			S2PThread thread = new S2PThread(recPkt);
			thread.start();
		}
	}
}

class S2PThread extends Thread{

	private DatagramPacket pkt;

	public S2PThread(DatagramPacket pkt) {
		this.pkt = pkt;
	}

	public void run() {
		try {
			/*
			//cria canal de leitura de informações do socket
			InputStreamReader is = new InputStreamReader(node.getInputStream());
			BufferedReader reader = new BufferedReader(is);

			//cria canal de escrita de dados no socket
			OutputStream os = node.getOutputStream();
			DataOutputStream writer = new DataOutputStream(os);

			//leitura do socket
			String texto = reader.readLine(); //BLOCKING
			System.out.println("Peer " + node.getInetAddress().getHostAddress() + " disse: " + texto);

			//escrita no socket
			writer.writeBytes("concordo");

			node.close(); */
			Mensagem msg = Peer.BytestoMsg(pkt.getData());

			switch (msg.reqtype) {
				case "JOIN":
					joinOk(msg.fileNames, pkt.getAddress(), pkt.getPort());
				case "LEAVE":
					leaveOk(pkt.getAddress(), pkt.getPort());
				case "SEARCH":
					search(msg.fileNames[0], pkt.getAddress(), pkt.getPort());
				case "UPDATE":
					update(msg.fileNames[0], pkt.getAddress(), pkt.getPort());
			}
		} catch(Exception e) {
			
		}
	}

	private static void joinOk(String filesNames[], InetAddress ipAddress, int port) throws IOException {

		/*ARMAZENAR OS DADOS */

		Mensagem msg = new Mensagem("JOIN_OK");
		Peer.sendMsg(msg, ipAddress, port);
	}

	private static void leaveOk(InetAddress ipAddress, int port) throws IOException {

		/* EXCLUIR OS DADOS */

		Mensagem msg = new Mensagem("LEAVE_OK");
		Peer.sendMsg(msg, ipAddress, port);
	}

	private static void search(String fileName, InetAddress ipAddress, int port) throws IOException {

		/*PROCURA O ARQUIVO */

		String peers[] = null;
		Mensagem msg = new Mensagem("SEARCH", peers);
		Peer.sendMsg(msg, ipAddress, port);
	}

	private static void update(String fileName, InetAddress ipAddress, int port) throws IOException {

		/*ATUALIZA OS DADOS */

		Mensagem msg = new Mensagem("UPDATE_OK");
		Peer.sendMsg(msg, ipAddress, port);
	}
}