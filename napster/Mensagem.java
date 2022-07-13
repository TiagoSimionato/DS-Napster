package napster;

import java.io.Serializable;

public class Mensagem implements Serializable {
	//Tipo de requisição a ser enviada
	public String reqtype;
	public String fileNames[];

	public Mensagem(String reqtype) {
		this.reqtype = reqtype;
	}

	public Mensagem(String reqtype, String fileNames[]) {
		this.reqtype = reqtype;
		this.fileNames = fileNames; /*TALVEZ EU SO ESTEJA MANDANDO UM PONTEIRO */
	}
}
