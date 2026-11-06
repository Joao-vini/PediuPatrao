const assert=require('node:assert/strict');
const fs=require('node:fs');

module.exports=async function({evaluate,request,login,navegar,esperar,confirmar,gerente}) {
    const fixture=JSON.parse(fs.readFileSync(process.env.PP_BROWSER_DEMO_EXPECTED,'utf8'));
    const id=fixture.pedidos.find(p=>p.codigo.startsWith('P2 ')).id;
    async function pedido() {
        const resposta=await request('/api/pedidos');assert.equal(resposta.status,200);
        return JSON.parse(resposta.body).find(p=>p.id===id);
    }
    async function logout() {
        const token=await evaluate('document.querySelector("input[name=_csrf]").value');
        await request('/logout',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({_csrf:token}).toString()});
    }
    async function indicadores(valor,ticket,desconto) {
        await logout();await login(process.env.PP_BROWSER_USER,process.env.PP_BROWSER_PASSWORD);
        const r=await request('/api/admin/dashboard/indicadores?inicio=2026-10-01&fim=2026-10-07');
        assert.equal(r.status,200);const i=JSON.parse(r.body);
        assert.equal(i.faturamento,valor);assert.equal(i.ticketMedio,ticket);assert.equal(i.descontos,desconto);
        await logout();await login(gerente.username,gerente.password);
    }
    await logout();await login(gerente.username,gerente.password);await navegar('/pedidos/'+id);
    assert.ok(await evaluate('document.querySelector("#descontoDetalhe") !== null'));
    await evaluate('document.querySelector("#descontoDetalhe").value="5";document.querySelector("#descontoDetalhe").form.requestSubmit()');
    await esperar('document.querySelector("#descontoDetalhe")?.value === "5.00"');
    const corrigido=await pedido();
    assert.equal(corrigido.valorTotal,61.75);assert.equal(corrigido.valorDesconto,3.25);
    await indicadores(491.75,70.25,3.25);
    await navegar('/pedidos/'+id);confirmar();
    await evaluate('Array.from(document.querySelectorAll("form")).find(f=>f.querySelector("input[name=tipoDesconto][value=SEM_DESCONTO]")).requestSubmit()');
    await esperar('document.querySelector("#descontoDetalhe")?.value === "0.00"');
    const removido=await pedido();
    assert.equal(removido.valorTotal,65);assert.equal(removido.valorDesconto,0);assert.equal(removido.tipoDesconto,'SEM_DESCONTO');
    assert.deepEqual(removido.itens,corrigido.itens);
    for(const operacao of ['DESCONTO_REMOVIDO','DESCONTO_SUBSTITUIDO']) {
        const eventos=await request('/auditoria?entidade=PEDIDO&operacao='+operacao);assert.equal(eventos.status,200);
        assert.ok(eventos.body.includes(id),'A auditoria filtrada deve conter o pedido alterado');
    }
    await indicadores(495,70.71,0);
    // Restaura a fixture pela API existente para a apresentação continuar coerente.
    await navegar('/pedidos/'+id);
    const token=await evaluate('document.querySelector("input[name=_csrf]").value');
    const restauro=await request('/api/pedidos/'+id+'/desconto?percentual=10',{method:'POST',headers:{'X-CSRF-TOKEN':token}});
    assert.equal(restauro.status,200);
    console.log('OK DESCONTOS: correção e remoção pela interface com confirmação, auditoria, itens preservados e indicadores 491.75/495.00 conferidos; fixture restaurada pela API.');
};
