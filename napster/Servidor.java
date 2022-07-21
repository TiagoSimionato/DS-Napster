package napster;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class Servidor {
	static DatagramSocket serverSocket;
	protected static List<String> peerFiles;
	protected static ArrayList<String> alivePeers = new ArrayList<String>();
	public static void main(String args[]) throws Exception{
		peerFiles = new ArrayList<>();
		serverSocket = new DatagramSocket(10098, InetAddress.getByName("127.0.0.1"));
		System.out.println("Server iniciado em " + serverSocket.getLocalAddress() + ":" + serverSocket.getLocalPort());

		//Inicializo a thread que verifica se os peers estão vivos
		AliveSender as = new AliveSender();
		as.start();

		while(true) {
			byte[] recBuffer = new byte[1024];

			DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);

			serverSocket.receive(recPkt); //BLOCKING

			S2PThread thread = new S2PThread(recPkt);
			thread.start();
		}
	}

	public static String[] arrayLisit2Array(ArrayList<String> l) {
		String[] array = new String[l.size()];

		for (int i = 0; i < l.size(); i++) {
			array[i] = l.get(i);
		}
		return array;
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
			//TODO: REMOVE: System.out.println("Mensagem " + msg.reqtype  + " recebida de: " + pkt.getSocketAddress());

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
				case "ALIVE_OK":
					//Substring para remover uma / inicial
					Servidor.alivePeers.add((pkt.getSocketAddress() + "").substring(1));
					break;
			}
		} catch(Exception e) {
			
		}
	}

	private void joinOk(Mensagem msg, InetAddress ipAddress, int port) throws IOException {
		String tcpDownloadPort = msg.fileNames[0];

		for (int i = 0; i < Servidor.peerFiles.size(); i++) {
			String[] slices = Servidor.peerFiles.get(i).split(":");
			String iPeer = slices[0] + slices[1];

			String newPeer = ipAddress.getHostAddress() + port;
			if (iPeer.compareTo(newPeer) == 0) {
				System.out.println("Peer já adicionado na rede!");
				return;
			}
		}

		String[] files = new String[msg.fileNames.length - 1];

		//Se o peer não tiver arquivos, apenas guardo um registro que ele está na rede
		if (files.length == 0) {
			Servidor.peerFiles.add(ipAddress.getHostAddress() + ":" + port + ":" + tcpDownloadPort + ",");
		}
		//Armazena os arquivos como nomedopeer,arquivo
		for (int i = 1; i < msg.fileNames.length; i++) {
			Servidor.peerFiles.add(ipAddress.getHostAddress() + ":" + port + ":" + tcpDownloadPort + "," + msg.fileNames[i]);
			files[i - 1] = msg.fileNames[i];
		}


		//Crio a mensagem de join ok
		Mensagem answer = new Mensagem("JOIN_OK", files);
		//Uso o metodo do peer que envia mensagens
		Peer.sendMsg(Servidor.serverSocket, answer, ipAddress, port);

		System.out.println("Peer " + ipAddress.getHostAddress() + ":" + port + ":" + tcpDownloadPort + " adicionado com arquivos " + Peer.stringArrayConcat(files));
		System.out.print("\nArquivos do servidor: [");
		for (int i = 0 ; i < Servidor.peerFiles.size(); i++) {
			System.out.print(Servidor.peerFiles.get(i) + " | ");
		}
		System.out.println("]\n");
	}

	private void leaveOk(InetAddress ipAddress, int port) throws IOException {
		//Excluo os dados do peer
		for(int i = 0; i < Servidor.peerFiles.size(); i++) {
			//Substring é usado para tirar a / inicial
			if (Servidor.peerFiles.get(i).contains( (pkt.getSocketAddress() + "").substring(1) )) {
				Servidor.peerFiles.remove(i);
				i--;
			}
		}
		
		//Crio a mensagem de resposta
		Mensagem answer = new Mensagem("LEAVE_OK", null);
		//Uso o método do peer que envia mensagens
		Peer.sendMsg(Servidor.serverSocket, answer, ipAddress, port);
		System.out.print("\nArquivos do servidor: [");
		for (int i = 0 ; i < Servidor.peerFiles.size(); i++) {
			System.out.print(Servidor.peerFiles.get(i) + " | ");
		}
		System.out.println("]\n");
	}

	private void search(String fileName, InetAddress ipAddress, int port) throws IOException {
		System.out.println(("Peer " + ipAddress + ":" + port + " soliticou o arquivo " + fileName));

		ArrayList<String> peersL = new ArrayList<String>();

		for (int i = 0; i < Servidor.peerFiles.size(); i++) {
			if (Servidor.peerFiles.get(i).contains(fileName)) {
				//Adiciono o IP, que está antes do ':'
				peersL.add(Servidor.peerFiles.get(i).split(",")[0]);
			}
		}

		//Converto o arraylist para array
		String peers[] = Servidor.arrayLisit2Array(peersL);
		/*if (peersL.size() > 0) {
			peers = new String[peersL.size()];
			for (int i = 0; i < peersL.size(); i++) {
				peers[i] = peersL.get(i);
			}
		}
		else {
			peers = null;
		}*///TODO: REMOVE COMMENT

		//Envido a resposta do search
		Mensagem msg = new Mensagem("SEARCH", peers);
		Peer.sendMsg(Servidor.serverSocket, msg, ipAddress, port);
	}

	private static void update(String fileName, InetAddress ipAddress, int port) throws IOException {

		//Preciso procurar o tcpPort do peer
		String tcpPort = "";
		for (int i = 0; i < Servidor.peerFiles.size(); i++) {
			String iPeer = Servidor.peerFiles.get(i).split(",")[0];
			if (iPeer.contains(ipAddress.getHostAddress() + ":" + port)) {
				tcpPort = iPeer.split(":")[2];
				break;
			}
		}

		//Adiciono o novo arquivo do peer na lista de arquivos
		Servidor.peerFiles.add(ipAddress.getHostAddress() + ":" + port + ":" + tcpPort +  "," + fileName);

		//Envio o updateOk para o peer
		Mensagem answer = new Mensagem("UPDATE_OK");
		Peer.sendMsg(Servidor.serverSocket, answer, ipAddress, port);
	}
}

class AliveSender extends Thread {

	public void run() {
		try {
			//Alive é enviado a cada 30s aproximadamente
			AliveSender.sleep(30000);

			//Inicio a lista de peers que receberão a req Alive
			ArrayList<String> networKPeers = new ArrayList<String>();
			networKPeers.clear();
			for (int i = 0; i < Servidor.peerFiles.size(); i++) {
				//Pego o ip/porta do peer i
				String[] slices = Servidor.peerFiles.get(i).split(":");
				String iPeerIpPort = slices[0] + ":" + slices[1];
				
				if (i == 0) {
					networKPeers.add(iPeerIpPort);
				}

				//Procuro se o i-ésimo peer ja está na lista da rede
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

			//Envios as requisições alive para os peers que responderam
			for (int i = 0; i < networKPeers.size(); i++) {
				Mensagem msg = new Mensagem("ALIVE");

				//String vem no formato ip:porta,nomedo.arquivo
				String[] ipport = networKPeers.get(i).split(":");
				String ip = ipport[0];
				int port = Integer.valueOf(ipport[1]);

				Peer.sendMsg(Servidor.serverSocket, msg, InetAddress.getByName(ip), port);
			}

			//Espero por 3 segundos
			AliveSender.sleep(3000);

			//Elimino os dados dos peers que não estão vivos
			for (int i = 0; i < networKPeers.size(); i++) {
				String peerI = networKPeers.get(i);
				//Se o peeri não esta vivo
				if (!Servidor.alivePeers.contains(peerI)) {

					//Itero sobre peerFiles para remover os arquivos do peer morto
					for (int j = 0; j < Servidor.peerFiles.size(); j++) {
						if (Servidor.peerFiles.get(j).contains(peerI)) {
							Servidor.peerFiles.remove(j);
							j--;
						}
					}
					System.out.println("Peer " + networKPeers.get(i) + " morto. Eliminando seus arquivos"); //TODO: LISTAR OS
				}
			}

			//Limpo o Array de alivePeers para a proxima iteração de Alives reqs
			Servidor.alivePeers.clear();

			AliveSender nextIt = new AliveSender();
			nextIt.start();
			this.interrupt();;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}