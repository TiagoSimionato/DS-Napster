package napster;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class Servidor {
	static DatagramSocket serverSocket;
	static protected List<String> peerFiles;
	public static void main(String args[]) throws Exception{
		peerFiles = new ArrayList<>();
		serverSocket = new DatagramSocket(10098, InetAddress.getByName("127.0.0.1"));
		System.out.println("Server iniciado em " + serverSocket.getLocalAddress() + ":" + serverSocket.getLocalPort());

		while(true) {
			byte[] recBuffer = new byte[1024];

			DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);

			serverSocket.receive(recPkt); //BLOCKING
			System.out.println("PACOTE RECEBIDO de " + recPkt.getAddress() + ":" + recPkt.getPort());

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
			Mensagem msg = Peer.BytestoMsg(pkt.getData());
			System.out.println("Mensagem " + msg.reqtype  + "recebida de: " + pkt.getSocketAddress());

			switch (msg.reqtype) {
				case "JOIN":
					joinOk(msg, pkt.getAddress(), pkt.getPort());
					break;
				case "LEAVE":
					leaveOk(pkt.getAddress(), pkt.getPort());
					break;
				case "SEARCH":
					search(msg.fileNames[0], pkt.getAddress(), pkt.getPort());
					break;
				case "UPDATE":
					update(msg.fileNames[0], pkt.getAddress(), pkt.getPort());
					break;
			}
		} catch(Exception e) {
			
		}
	}

	private void joinOk(Mensagem msg, InetAddress ipAddress, int port) throws IOException {
		//Armazena os arquivos como nomedopeer,arquivo
		for (int i = 0; i < msg.fileNames.length; i++) {
			Servidor.peerFiles.add(ipAddress.getHostAddress() + ":" + port + "," + msg.fileNames[i]);
		}

		//Crio a mensagem de join ok
		Mensagem answer = new Mensagem("JOIN_OK", msg.fileNames);
		//Uso o metodo do peer que envia mensagens
		Peer.sendMsg(Servidor.serverSocket, answer, ipAddress, port);
		System.out.println("Peer " + ipAddress.getHostAddress() + ":" + port + " adicionado com arquivos " + Peer.stringArrayConcat(msg.fileNames));
		System.out.print("Arquivos do servidor: [");
		for (int i = 0 ; i < Servidor.peerFiles.size(); i++) {
			System.out.print(Servidor.peerFiles.get(i) + " | ");
		}
		System.out.println("]");
	}

	private void leaveOk(InetAddress ipAddress, int port) throws IOException {
		//Excluo os dados do peer
		for(int i = 0; i < Servidor.peerFiles.size(); i++) {
			if (Servidor.peerFiles.get(i).contains(pkt.getSocketAddress() + "")) {
				Servidor.peerFiles.remove(i);
			}
		}
		
		//Crio a mensagem de resposta
		Mensagem answer = new Mensagem("LEAVE_OK", null);
		//Uso o método do peer que envia mensagens
		Peer.sendMsg(Servidor.serverSocket, answer, ipAddress, port);
		System.out.println("Peer " + ipAddress + ":" + port + " excluido dos arquivos ");
		System.out.print("Arquivos do servidor: [");
		for (int i = 0 ; i < Servidor.peerFiles.size(); i++) {
			System.out.print(Servidor.peerFiles.get(i) + " | ");
		}
		System.out.println("]");
	}

	private void search(String fileName, InetAddress ipAddress, int port) throws IOException {
		List<String> peersL = new ArrayList<String>();

		for (int i = 0; i < Servidor.peerFiles.size(); i++) {
			if (Servidor.peerFiles.get(i).contains(fileName)) {
				//Adiciono o IP, que está antes do ':'
				peersL.add(Servidor.peerFiles.get(i).split(",")[0]);
			}
		}

		String peers[];
		if (peersL.size() > 0) {
			peers = new String[peersL.size()];
			for (int i = 0; i < peersL.size(); i++) {
				peers[i] = peersL.get(i);
			}
		}
		else {
			peers = null;
		}
		System.out.println("Peers encontrados: [" + Peer.stringArrayConcat(peers) + "]");

		Mensagem msg = new Mensagem("SEARCH", peers);
		Peer.sendMsg(Servidor.serverSocket, msg, ipAddress, port);
	}

	private static void update(String fileName, InetAddress ipAddress, int port) throws IOException {

		/*ATUALIZA OS DADOS */

		Mensagem msg = new Mensagem("UPDATE_OK");
		Peer.sendMsg(Servidor.serverSocket, msg, ipAddress, port);
	}
}