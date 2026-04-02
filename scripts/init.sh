#!/bin/bash

# ============================================
# Script de Inicialização - Todo Monorepo
# ============================================

set -e

echo "============================================"
echo "🚀 Inicializando Todo Monorepo"
echo "============================================"

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Verificar Docker
if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ Docker não encontrado. Por favor, instale o Docker.${NC}"
    exit 1
fi

if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo -e "${RED}❌ Docker Compose não encontrado. Por favor, instale o Docker Compose.${NC}"
    exit 1
fi

# Verificar Java
if ! command -v java &> /dev/null; then
    echo -e "${YELLOW}⚠️ Java não encontrado. Os serviços backend não poderão ser executados localmente.${NC}"
fi

# Verificar Node.js
if ! command -v node &> /dev/null; then
    echo -e "${YELLOW}⚠️ Node.js não encontrado. O frontend não poderá ser executado localmente.${NC}"
fi

echo -e "${GREEN}✅ Pré-requisitos verificados${NC}"
echo ""

# Menu de opções
show_menu() {
    echo "Selecione uma opção:"
    echo ""
    echo "  1) Subir todos os serviços (Docker)"
    echo "  2) Subir serviços backend apenas (Docker)"
    echo "  3) Subir apenas banco de dados (Docker)"
    echo "  4) Parar todos os serviços"
    echo "  5) Parar e remover volumes"
    echo "  6) Ver logs"
    echo "  7) Build das imagens"
    echo "  8) Sair"
    echo ""
}

# Verificar se docker-compose é v1 ou v2
if docker compose version &> /dev/null; then
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
fi

# Loop do menu
while true; do
    show_menu
    read -p "Opção: " option
    
    case $option in
        1)
            echo -e "${GREEN}🚀 Subindo todos os serviços...${NC}"
            $DOCKER_COMPOSE up -d
            echo -e "${GREEN}✅ Serviços iniciados!${NC}"
            echo ""
            echo "Serviços disponíveis:"
            echo "  - Eureka Dashboard: http://localhost:8761"
            echo "  - API Gateway:      http://localhost:8080"
            echo "  - User Service:     http://localhost:8081"
            echo "  - Todo Service:     http://localhost:8082"
            echo "  - Web App:          http://localhost:3000"
            ;;
        
        2)
            echo -e "${GREEN}🚀 Subindo serviços backend...${NC}"
            $DOCKER_COMPOSE up -d postgres redis eureka gateway user-service todo-service
            echo -e "${GREEN}✅ Serviços backend iniciados!${NC}"
            ;;
        
        3)
            echo -e "${GREEN}🚀 Subindo banco de dados...${NC}"
            $DOCKER_COMPOSE up -d postgres redis
            echo -e "${GREEN}✅ Banco de dados iniciado!${NC}"
            ;;
        
        4)
            echo -e "${YELLOW}⏹️ Parando serviços...${NC}"
            $DOCKER_COMPOSE down
            echo -e "${GREEN}✅ Serviços parados!${NC}"
            ;;
        
        5)
            echo -e "${RED}⚠️ Parando e removendo volumes...${NC}"
            $DOCKER_COMPOSE down -v
            echo -e "${GREEN}✅ Volumes removidos!${NC}"
            ;;
        
        6)
            echo -e "${GREEN}📋 Visualizando logs (Ctrl+C para sair)...${NC}"
            $DOCKER_COMPOSE logs -f
            ;;
        
        7)
            echo -e "${GREEN}🔨 Build das imagens...${NC}"
            $DOCKER_COMPOSE build
            echo -e "${GREEN}✅ Build concluído!${NC}"
            ;;
        
        8)
            echo -e "${GREEN}👋 Saindo...${NC}"
            exit 0
            ;;
        
        *)
            echo -e "${RED}❌ Opção inválida${NC}"
            ;;
    esac
    
    echo ""
done