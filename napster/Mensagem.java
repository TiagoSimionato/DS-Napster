package napster;

import java.io.Serializable;

public class Mensagem implements Serializable {
	//Cabecalho da mensagem
	/**Campo que indica o tipo de requisição a ser enviada */
	public String reqtype;
	/** String com diferentes significados dependendo da requisição feita
	 * <p>
	 * No join possui o nome de arquivos que o peer tem
	 * </p><p>
	 * No search e no update apenas o nome de um arquivo
	 * <p></p>
	 * Na resposta do search possui uma lista com os peers que possuem o arquivo procurado
	 * </p>
	 */
	public String fileNames[];

	/** Construtor usado quando apenas o tipo de requisição basta para a troca de mensagem, como o ALIVE_OK */
	public Mensagem(String reqtype) {
		this.reqtype = reqtype;
		fileNames = null;
	}

	public Mensagem(String reqtype, String fileNames[]) {
		this.reqtype = reqtype;
		this.fileNames = fileNames;
	}
}
